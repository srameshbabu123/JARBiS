"""Only run against an explicitly named disposable database; these tests drop schemas."""
from copy import deepcopy
from datetime import date
from decimal import Decimal
import os
from uuid import uuid4
import pytest
from analytics.config import Settings
from analytics.database import make_pool, migrate
from analytics.models import ReportFilter, SettlementEvent
from analytics.projector import project
from analytics.reports import Reports
from analytics.sessions import Sessions

pytestmark = pytest.mark.postgres


@pytest.fixture(scope="module")
def pool():
    url = os.getenv("TEST_POSTGRES_URL", "").removeprefix("jdbc:")
    from urllib.parse import urlparse
    if urlparse(url).path != "/admin_dashboard_test":
        pytest.skip("Set TEST_POSTGRES_URL to disposable database admin_dashboard_test")
    settings = Settings(url, os.getenv("TEST_POSTGRES_USER", "test"), os.getenv("TEST_POSTGRES_PASSWORD", "test"), "analyst", "unused")
    pool = make_pool(settings)
    pool.open(wait=True)
    with pool.connection() as conn:
        conn.execute("DROP SCHEMA IF EXISTS analytics CASCADE")
        conn.execute("DROP SCHEMA IF EXISTS reporting_migrations CASCADE")
    migrate(pool)
    migrate(pool)  # migrations can run repeatedly without losing data
    yield pool
    pool.close()


@pytest.fixture(autouse=True)
def clear(request):
    if request.node.get_closest_marker("postgres"):
        pool = request.getfixturevalue("pool")
        with pool.connection() as conn:
            conn.execute("TRUNCATE analytics.trade_participants,analytics.processed_events,analytics.transaction_facts,analytics.dim_accounts,analytics.dim_users,analytics.dim_assets,analytics.internal_sessions CASCADE")


def filt(**kw):
    return ReportFilter(**{"from": "2026-03-01", "to": "2026-03-02", **kw})


def test_duplicate_delivery_currency_dates_clients_and_pandas(pool, event_data):
    event = SettlementEvent.model_validate(event_data)
    project(pool, event)
    project(pool, event)
    project(pool, event.model_copy(update={"eventId": uuid4()}))
    for id, at, currency, seller in [(2,"2026-03-02T23:59:59Z","USD",3),(3,"2026-03-03T00:00:00Z","USD",4),(4,"2026-03-02T12:00:00Z","EUR",5)]:
        payload = deepcopy(event_data)
        payload.update(eventId=str(uuid4()), transactionId=id, occurredAt=at, currency=currency, quantity="3", executionPrice="20")
        payload["seller"].update(userId=seller, accountId=20+seller, orderId=id*2+1)
        payload["buyer"]["orderId"] = id*2
        project(pool, SettlementEvent.model_validate(payload))
    reports = Reports(pool)
    dashboard = reports.dashboard(filt())
    assert dashboard["summary"] == {"trades": 2, "value": Decimal("80"), "clients": 3, "instruments": 1}
    assert reports.dashboard(filt(segment="INVESTMENT"))["summary"]["clients"] == 1
    assert len(dashboard["activity"]) == 2
    assert reports.trades(filt(), 0)["total"] == 2
    assert dashboard["ingestion"]["processed_events"] == 5
    assert reports.options()["instruments"][0]["name"] == "AAPL"


def test_projection_failure_rolls_back_dedup_marker(pool, event_data):
    with pool.connection() as conn:
        conn.execute("ALTER TABLE analytics.transaction_facts ADD CONSTRAINT test_reject CHECK (transaction_id <> 1)")
    try:
        with pytest.raises(Exception):
            project(pool, SettlementEvent.model_validate(event_data))
        with pool.connection() as conn:
            assert conn.execute("SELECT COUNT(*) AS n FROM analytics.processed_events").fetchone()["n"] == 0
            assert conn.execute("SELECT COUNT(*) AS n FROM analytics.transaction_facts").fetchone()["n"] == 0
    finally:
        with pool.connection() as conn:
            conn.execute("ALTER TABLE analytics.transaction_facts DROP CONSTRAINT test_reject")
    project(pool, SettlementEvent.model_validate(event_data))  # same event is retryable


def test_legacy_collision_is_rejected_and_datasets_remain_separate(pool, event_data):
    project(pool, SettlementEvent.model_validate(event_data))
    with pool.connection() as conn:
        conn.execute("UPDATE analytics.transaction_facts SET data_source='LEGACY'")
    assert Reports(pool).dashboard(filt())["summary"]["trades"] == 0
    assert Reports(pool).dashboard(filt(dataset="LEGACY"))["summary"]["trades"] == 1
    event_data["eventId"] = str(uuid4())
    with pytest.raises(ValueError):
        project(pool, SettlementEvent.model_validate(event_data))


def test_server_side_session_expiry_and_revocation(pool):
    sessions = Sessions(pool)
    raw, _ = sessions.create("analyst")
    assert sessions.find(raw)["username"] == "analyst"
    with pool.connection() as conn:
        row = conn.execute("SELECT token_hash FROM analytics.internal_sessions").fetchone()
        assert row["token_hash"] != raw
        conn.execute("UPDATE analytics.internal_sessions SET expires_at=now()-interval '1 minute'")
    assert sessions.find(raw) is None
    raw, _ = sessions.create("analyst")
    sessions.revoke(raw)
    assert sessions.find(raw) is None


def test_real_api_startup_login_and_report(pool, event_data):
    import bcrypt
    from fastapi.testclient import TestClient
    from analytics.api import create_app
    project(pool, SettlementEvent.model_validate(event_data))
    settings = Settings(os.environ["TEST_POSTGRES_URL"].removeprefix("jdbc:"), os.environ.get("TEST_POSTGRES_USER", "test"),
                        os.environ.get("TEST_POSTGRES_PASSWORD", "test"), "analyst", bcrypt.hashpw(b"test-only", bcrypt.gensalt(rounds=4)).decode())
    with TestClient(create_app(settings)) as client:
        assert client.get("/actuator/health").status_code == 200
        csrf = client.get("/session/csrf").json()["token"]
        assert client.post("/login", data={"username": "analyst", "password": "test-only"}, headers={"X-CSRF-TOKEN": csrf}).status_code == 204
        response = client.get("/admin/analytics/dashboard?from=2026-03-01&to=2026-03-02")
        assert response.status_code == 200
        assert response.json()["summary"] == {"trades": 1, "value": 20, "clients": 2, "instruments": 1}
        assert client.get("/admin/analytics/trades?from=2026-03-01&to=2026-03-02").json()["total"] == 1

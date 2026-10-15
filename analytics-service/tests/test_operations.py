from datetime import datetime, timedelta, timezone
from decimal import Decimal
from dataclasses import replace
import bcrypt
import pandas as pd
import pytest
from fastapi.testclient import TestClient
from analytics.api import create_app
from analytics.config import Settings
from analytics.operations import dispute_insights, safe_csv
from analytics.models import DisputeCreate, DisputeChange, DisputeNote, LifecycleEvent
from pydantic import ValidationError
from test_api import FakeSessions, FakeReports


def test_pandas_dispute_aging_and_resolution():
    now = datetime(2026, 10, 15, tzinfo=timezone.utc)
    rows = [{"status": "OPEN", "category": "HOLDINGS", "opened_at": now-timedelta(days=2), "resolved_at": None},
            {"status": "RESOLVED", "category": "EXECUTION_PRICE", "opened_at": now-timedelta(days=4), "resolved_at": now-timedelta(days=3)},
            {"status": "IN_REVIEW", "category": "OTHER", "opened_at": now-timedelta(days=40), "resolved_at": now-timedelta(days=5)}]
    result = dispute_insights(rows, now)
    assert result["total"] == 3
    assert result["medianResolutionHours"] == 24
    assert {r["bucket"]: r["count"] for r in result["openAgeBuckets"]}["30+ days"] == 1
    assert dispute_insights([])["total"] == 0


def test_csv_formula_injection_is_neutralized():
    result = safe_csv(pd.DataFrame([{"note": "=HYPERLINK(\"bad\")", "quantity": Decimal("-2")}]))
    assert "'=HYPERLINK" in result
    assert "-2" in result


def test_empty_case_reason_and_notes_are_rejected():
    from uuid import uuid4
    with pytest.raises(ValidationError): DisputeCreate(requestId=uuid4(), orderId=1, category="OTHER", reason=" "*20)
    with pytest.raises(ValidationError): DisputeNote(text="  ")
    with pytest.raises(ValidationError): DisputeChange(expectedVersion=0, status="RESOLVED")


class FakeOperations:
    def timeline(self, id, actor): return {"orderId": id, "actor": actor, "coverageGaps": ["quote unavailable"]}
    def create(self, body, actor): return {"orderId": body.orderId, "openedBy": actor}
    def change(self, id, body, actor): return {"status": body.status}
    def insights(self, f, actor): return {"total": 0}


def sign_in(client, name, password):
    token=client.get("/session/csrf").json()["token"]
    response=client.post("/login",data={"username":name,"password":password},headers={"X-CSRF-TOKEN":token})
    assert response.status_code == 204
    return client.get("/session/csrf").json()["token"]


def test_operations_role_and_csrf_boundary():
    password_hash=bcrypt.hashpw(b"test-only",bcrypt.gensalt(rounds=4)).decode()
    settings=Settings("postgresql://localhost/test","test","test","priya",password_hash,operations_username="david",operations_password_hash=password_hash)
    app=create_app(settings,sessions=FakeSessions(),reports=FakeReports(),operations=FakeOperations())
    with TestClient(app) as client:
        sign_in(client,"priya","test-only")
        assert client.get("/admin/analytics/dashboard").status_code == 200
        assert client.get("/admin/investigations/orders/1/timeline").status_code == 403
        assert client.get("/admin/disputes/insights").status_code == 403
        assert client.get("/admin/analytics/trades").status_code == 403
        token=sign_in(client,"david","test-only")
        assert client.get("/session/me").json()["roles"] == ["ROLE_OPERATIONS"]
        assert client.get("/admin/investigations/orders/1/timeline").json()["actor"] == "david"
        from uuid import uuid4
        body={"requestId":str(uuid4()),"orderId":1,"category":"OTHER","reason":"Client disputes this order"}
        assert client.post("/admin/disputes",json=body).status_code == 403
        assert client.post("/admin/disputes",json=body,headers={"X-CSRF-TOKEN":token}).status_code == 201
        assert client.post("/admin/disputes",json={**body,"assignedTo":"priya"},headers={"X-CSRF-TOKEN":token}).status_code == 400
        assert client.get("/admin/disputes/insights?currency=USD").status_code == 400

from copy import deepcopy
from datetime import datetime, timezone
from uuid import uuid4
import pytest
from fastapi import HTTPException
from analytics.models import LifecycleEvent, DisputeCreate, DisputeChange, ReportFilter, SettlementEvent
from analytics.projector import project_lifecycle, project
from analytics.operations import Operations
from test_postgres import pool, clear

pytestmark=pytest.mark.postgres


def lifecycle(sequence=1, kind="ORDER_SUBMITTED", status="PENDING"):
    return LifecycleEvent.model_validate({"schemaVersion":1,"eventId":str(uuid4()),"type":"OrderLifecycle","auditSequence":sequence,
        "occurredAt":"2026-10-15T12:00:00Z","orderId":2,"clientId":1,"accountId":10,"assetId":8,"eventType":kind,
        "instrument":"AAPL","side":"BUY","status":status,"quantity":"2","remainingQuantity":"2" if status=="PENDING" else "0",
        "orderPrice":"100","cashBalance":"800","currency":"USD","holdingQuantity":"2","actor":"SYSTEM",
        "details":{"marketQuoteRecorded":False,"priceBasis":"RESTING_ORDER_PRICE","before":{"cashBalance":"1000","holdingQuantity":"0"},"after":{"cashBalance":"800","holdingQuantity":"2"}}})


def test_lifecycle_replay_ordering_exports_and_immutability(pool):
    last=lifecycle(3,"FILL_RECORDED","COMPLETED")
    first=lifecycle()
    project_lifecycle(pool,last);project_lifecycle(pool,first);project_lifecycle(pool,first)
    ops=Operations(pool)
    result=ops.timeline(2,"david")
    assert [r["audit_sequence"] for r in result["timeline"]] == [1,3]
    assert result["eventCount"] == 2
    assert result["marketQuoteEvidenceAvailable"] is False
    assert any("quote" in gap for gap in result["coverageGaps"])
    f=ReportFilter(**{"from":"2026-10-01","to":"2026-10-31"})
    assert ops.search(f,"david",status="COMPLETED")["total"] == 1
    assert "COVERAGE_GAP" in ops.export(2,"david")
    with pytest.raises(Exception):
        with pool.connection() as conn: conn.execute("UPDATE analytics.order_events SET event_type='edited'")
    with pytest.raises(Exception):
        with pool.connection() as conn: conn.execute("DELETE FROM analytics.order_events")
    with pool.connection() as conn:
        assert conn.execute("SELECT COUNT(*) AS n FROM operations.access_events").fetchone()["n"] >= 3


def test_dispute_workflow_snapshot_versioning_and_retention(pool):
    project_lifecycle(pool,lifecycle())
    ops=Operations(pool)
    body=DisputeCreate(requestId=uuid4(),orderId=2,category="EXECUTION_PRICE",reason="The client disputes the execution price",assignedTo="david")
    opened=ops.create(body,"david")
    assert opened["client_id"] == 1
    assert ops.create(body,"david")["id"] == opened["id"]
    id=opened["id"]
    with pytest.raises(HTTPException) as ex:
        ops.create(body.model_copy(update={"reason":"A different reason for the same request"}),"david")
    assert ex.value.status_code == 409
    with pytest.raises(HTTPException): ops.change(id,DisputeChange(expectedVersion=1,status="CLOSED"),"david")
    reviewing=ops.change(id,DisputeChange(expectedVersion=1,status="IN_REVIEW"),"david")
    assert reviewing["version"] == 2
    with pytest.raises(HTTPException) as ex: ops.change(id,DisputeChange(expectedVersion=1,status="RESOLVED",resolution="Evidence checked and explained"),"david")
    assert ex.value.status_code == 409
    with pytest.raises(HTTPException): ops.change(id,DisputeChange(expectedVersion=2,status="RESOLVED"),"david")
    ops.note(id,"Reviewed pricing evidence; current quote is not available","david")
    resolved=ops.change(id,DisputeChange(expectedVersion=3,status="RESOLVED",resolution="Explained the recorded order-book pricing to the client"),"david")
    closed=ops.change(id,DisputeChange(expectedVersion=resolved["version"],status="CLOSED"),"david")
    with pytest.raises(HTTPException): ops.note(id,"cannot add to closed","david")
    with pytest.raises(HTTPException): ops.change(id,DisputeChange(expectedVersion=closed["version"],status="IN_REVIEW"),"david")
    reopened=ops.change(id,DisputeChange(expectedVersion=closed["version"],status="IN_REVIEW",note="Client provided additional evidence"),"david")
    assert reopened["resolution"] is None
    project_lifecycle(pool,lifecycle(2,"ORDER_STATUS_CHANGED","CANCELLED"))
    detail=ops.detail(id,"david")
    assert detail["evidence"]["eventCount"] == 1  # frozen at case opening
    assert len(detail["events"]) == 6
    assert ops.cases("david",status="IN_REVIEW")["total"] == 1
    assert ops.create(body,"david")["id"] == id  # idempotency remains valid after workflow updates
    with pytest.raises(Exception):
        with pool.connection() as conn: conn.execute("UPDATE operations.disputes SET evidence='{}'::jsonb WHERE id=%s",(id,))
    with pytest.raises(Exception):
        with pool.connection() as conn: conn.execute("DELETE FROM operations.dispute_events WHERE dispute_id=%s",(id,))
    f=ReportFilter(**{"from":"2000-01-01","to":"2000-01-02"})
    assert ops.insights(f,"david")["total"] == 0
    today=datetime.now(timezone.utc).date()
    f=ReportFilter(**{"from":today,"to":today})
    assert ops.insights(f,"david")["total"] == 1


def test_older_fill_has_explicit_history_gap_and_unknown_order_cannot_open_case(pool,event_data):
    project(pool,SettlementEvent.model_validate(event_data))
    ops=Operations(pool)
    evidence=ops.timeline(event_data["buyer"]["orderId"],"david")
    assert evidence["timeline"] == []
    assert any("predate" in gap for gap in evidence["coverageGaps"])
    assert ops.trade(event_data["transactionId"],"david")["transaction_id"] == event_data["transactionId"]
    with pytest.raises(HTTPException) as ex:
        ops.create(DisputeCreate(requestId=uuid4(),orderId=999,category="OTHER",reason="No corresponding evidence exists"),"david")
    assert ex.value.status_code == 404


def test_operations_api_end_to_end(pool):
    import bcrypt
    import os
    from fastapi.testclient import TestClient
    from analytics.api import create_app
    from analytics.config import Settings
    project_lifecycle(pool,lifecycle())
    hash=bcrypt.hashpw(b"test-only",bcrypt.gensalt(rounds=4)).decode()
    settings=Settings(os.environ["TEST_POSTGRES_URL"].removeprefix("jdbc:"),os.environ.get("TEST_POSTGRES_USER","test"),os.environ.get("TEST_POSTGRES_PASSWORD","test"),"priya",hash,operations_username="david",operations_password_hash=hash)
    with TestClient(create_app(settings)) as client:
        csrf=client.get("/session/csrf").json()["token"]
        assert client.post("/login",data={"username":"david","password":"test-only"},headers={"X-CSRF-TOKEN":csrf}).status_code == 204
        csrf=client.get("/session/csrf").json()["token"]
        header={"X-CSRF-TOKEN":csrf}
        assert client.get("/admin/investigations/orders/2/timeline").status_code == 200
        assert client.get("/admin/investigations/orders/2/export").headers["content-type"].startswith("text/csv")
        response=client.post("/admin/disputes",json={"requestId":str(uuid4()),"orderId":2,"category":"HOLDINGS","reason":"The client disputes the holding quantity","assignedTo":"david"},headers=header)
        assert response.status_code == 201
        id=response.json()["id"]
        assert client.patch(f"/admin/disputes/{id}",json={"expectedVersion":1,"status":"IN_REVIEW"},headers=header).status_code == 200
        assert client.post(f"/admin/disputes/{id}/notes",json={"text":"Evidence reviewed"},headers=header).status_code == 201
        assert client.get(f"/admin/disputes/{id}/evidence").json()["orderId"] == 2
        assert client.get("/admin/disputes").json()["total"] == 1
        assert client.get("/admin/disputes/insights").json()["total"] == 1

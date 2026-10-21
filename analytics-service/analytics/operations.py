"""David's investigations and dispute workflow. Never updates trading balances or fills."""
from datetime import datetime, timezone
from decimal import Decimal
from io import StringIO
from uuid import uuid4
import json
import pandas as pd
from fastapi import HTTPException
from psycopg.types.json import Jsonb


def serializable(value):
    return json.loads(json.dumps(value, default=str))


def safe_csv(frame):
    # Staff-supplied strings must not execute as spreadsheet formulas on export.
    def safe(value):
        return "'" + value if isinstance(value, str) and value.startswith(("=", "+", "-", "@", "\t", "\r")) else value
    return frame.map(safe).to_csv(index=False)


def dispute_insights(rows, now=None):
    if not rows:
        return {"total": 0, "byStatus": [], "byCategory": [], "dailyOpened": [], "openAgeBuckets": [], "medianResolutionHours": None}
    now = pd.Timestamp(now or datetime.now(timezone.utc))
    df = pd.DataFrame(rows)
    df["opened_at"] = pd.to_datetime(df["opened_at"], utc=True)
    status = [{"status": key, "count": int(n)} for key, n in df.groupby("status").size().items()]
    categories = [{"category": key, "count": int(n)} for key, n in df.groupby("category").size().items()]
    daily = [{"day": day.isoformat(), "count": int(n)} for day, n in df.groupby(df["opened_at"].dt.date).size().items()]
    active = df[df["status"].isin(["OPEN", "IN_REVIEW"])].copy()
    age = (now - active["opened_at"]).dt.total_seconds() / 86400
    buckets = pd.cut(age, bins=[-float("inf"), 1, 7, 30, float("inf")], labels=["0-1 days", "1-7 days", "7-30 days", "30+ days"], right=False)
    counts = buckets.value_counts(sort=False)
    resolution = []
    for row in rows:
        if row.get("resolved_at") and row["status"] in ("RESOLVED", "CLOSED"):
            resolution.append((pd.Timestamp(row["resolved_at"]) - pd.Timestamp(row["opened_at"])).total_seconds() / 3600)
    return {"total": len(rows), "byStatus": status, "byCategory": categories, "dailyOpened": daily,
            "openAgeBuckets": [{"bucket": str(key), "count": int(n)} for key, n in counts.items()],
            "medianResolutionHours": float(pd.Series(resolution).median()) if resolution else None}


class Operations:
    def __init__(self, pool):
        self.pool = pool

    @staticmethod
    def access(conn, actor, action, subject):
        conn.execute("INSERT INTO operations.access_events(actor,action,subject) VALUES (%s,%s,%s)", (actor, action, str(subject)))

    @staticmethod
    def evidence(conn, order_id):
        rows = conn.execute("SELECT audit_sequence,event_type,occurred_at,payload,ingested_at FROM analytics.order_events WHERE order_id=%s ORDER BY audit_sequence LIMIT 2001", (order_id,)).fetchall()
        total = conn.execute("SELECT COUNT(*) AS n FROM analytics.order_events WHERE order_id=%s", (order_id,)).fetchone()["n"]
        fills = conn.execute("SELECT * FROM analytics.transaction_facts WHERE buy_order_id=%s OR sell_order_id=%s ORDER BY transaction_timestamp,transaction_id LIMIT 1001", (order_id, order_id)).fetchall()
        if not rows and not fills:
            raise HTTPException(404, "Order evidence not available; check ingestion before opening a case")
        client_id = rows[0]["payload"]["clientId"] if rows else (fills[0]["buyer_user_id"] if fills[0]["buy_order_id"] == order_id else fills[0]["seller_user_id"])
        gaps = []
        if not any(row["event_type"] == "ORDER_SUBMITTED" for row in rows):
            gaps.append("Submission history is unavailable; this order may predate lifecycle capture.")
        quote_recorded = any(row["payload"].get("details", {}).get("marketQuoteRecorded") is True for row in rows)
        if not quote_recorded:
            gaps.append("Current market quote evidence is unavailable; the trading engine currently prices from orders.")
        if total > 2000 or len(fills) > 1000:
            gaps.append("This response is truncated; request an operational archive extraction for full evidence.")
        return {"orderId": order_id, "clientId": client_id, "timeline": rows[:2000], "fills": fills[:1000],
                "eventCount": total, "truncated": total > 2000 or len(fills) > 1000,
                "marketQuoteEvidenceAvailable": quote_recorded, "coverageGaps": gaps,
                "projectionAsOf": max((row["ingested_at"] for row in rows), default=None)}

    def timeline(self, order_id, actor):
        with self.pool.connection() as conn:
            conn.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ")
            result = self.evidence(conn, order_id)
            self.access(conn, actor, "ORDER_TIMELINE_VIEWED", order_id)
            return result

    def search(self, filters, actor, page=0, client_id=None, order_id=None, status=None):
        _, p = filters.sql()
        p.update(page=page * 25, client=client_id, order=order_id, status=status)
        conditions = " occurred_at >= %(start)s AND occurred_at < %(end)s "
        if client_id is not None: conditions += " AND client_id=%(client)s"
        if order_id is not None: conditions += " AND order_id=%(order)s"
        if status is not None: conditions += " AND payload->>'status'=%(status)s"
        source = "(SELECT DISTINCT ON(order_id) order_id,client_id,account_id,asset_id,occurred_at,audit_sequence,payload FROM analytics.order_events ORDER BY order_id,audit_sequence DESC) latest"
        with self.pool.connection() as conn:
            rows = conn.execute("SELECT * FROM " + source + " WHERE " + conditions + " ORDER BY occurred_at DESC,order_id DESC LIMIT 25 OFFSET %(page)s", p).fetchall()
            total = conn.execute("SELECT COUNT(*) AS n FROM " + source + " WHERE " + conditions, p).fetchone()["n"]
            self.access(conn, actor, "ORDER_SEARCH", json.dumps({"clientId": client_id, "orderId": order_id, "from": str(filters.from_), "to": str(filters.to)}))
            return {"rows": rows, "page": page, "pageSize": 25, "total": total, "source": "Lifecycle events; older fills may be available by exact order/trade ID"}

    def trade(self, trade_id, actor):
        with self.pool.connection() as conn:
            row = conn.execute("SELECT * FROM analytics.transaction_facts WHERE transaction_id=%s", (trade_id,)).fetchone()
            if not row: raise HTTPException(404, "Trade not found")
            self.access(conn, actor, "TRADE_VIEWED", trade_id)
            return row

    def export(self, order_id, actor):
        evidence = self.timeline(order_id, actor)
        rows = []
        for row in evidence["timeline"]:
            p = row["payload"]; d = p.get("details", {}); before = d.get("before", {}); after = d.get("after", {})
            rows.append({"sequence": row["audit_sequence"], "occurred_at": row["occurred_at"], "event_type": row["event_type"],
                         "order_id": order_id, "client_id": evidence["clientId"], "actor": p.get("actor"), "status": p.get("status"),
                         "execution_price": d.get("executionPrice"), "price_basis": d.get("priceBasis"), "market_quote_recorded": d.get("marketQuoteRecorded"),
                         "cash_before": before.get("cashBalance"), "cash_after": after.get("cashBalance"),
                         "holdings_before": before.get("holdingQuantity"), "holdings_after": after.get("holdingQuantity")})
        # Include coverage rows so an export cannot silently imply complete history.
        for gap in evidence["coverageGaps"]:
            rows.append({"order_id": order_id, "event_type": "COVERAGE_GAP", "status": gap})
        return safe_csv(pd.DataFrame(rows))

    @staticmethod
    def case_event(conn, id, actor, kind, details):
        conn.execute("INSERT INTO operations.dispute_events(dispute_id,actor,kind,details) VALUES (%s,%s,%s,%s)", (id, actor, kind, Jsonb(serializable(details))))

    def create(self, body, actor):
        with self.pool.connection() as conn:
            # Serialize retries of an idempotency key before reading/writing the case.
            conn.execute("SELECT pg_advisory_xact_lock(hashtextextended(%s,0))", (str(body.requestId),))
            existing = conn.execute("SELECT * FROM operations.disputes WHERE request_id=%s", (body.requestId,)).fetchone()
            if existing:
                opened = conn.execute("SELECT details FROM operations.dispute_events WHERE dispute_id=%s AND kind='OPENED' ORDER BY id LIMIT 1", (existing["id"],)).fetchone()
                original_assignee = opened["details"].get("assignedTo") if opened else existing["assigned_to"]
                if (existing["order_id"], existing["category"], existing["reason"], existing["opened_by"], original_assignee) != (body.orderId, body.category, body.reason, actor, body.assignedTo):
                    raise HTTPException(409, "requestId was used for a different dispute")
                return existing
            evidence = self.evidence(conn, body.orderId)
            id = uuid4()
            row = conn.execute("INSERT INTO operations.disputes(id,request_id,order_id,client_id,category,reason,status,assigned_to,opened_by,evidence) VALUES (%s,%s,%s,%s,%s,%s,'OPEN',%s,%s,%s) RETURNING *",
                               (id, body.requestId, body.orderId, evidence["clientId"], body.category, body.reason, body.assignedTo, actor, Jsonb(serializable(evidence)))).fetchone()
            self.case_event(conn, id, actor, "OPENED", {"reason": body.reason, "category": body.category, "assignedTo": body.assignedTo, "evidenceEventCount": evidence["eventCount"]})
            return row

    def cases(self, actor, page=0, status=None, client_id=None, order_id=None):
        parts = ["TRUE"]; p = {"offset": page * 25}
        for name, value in (("status", status), ("client_id", client_id), ("order_id", order_id)):
            if value is not None:
                parts.append(name + "=%(" + name + ")s"); p[name] = value
        where = " AND ".join(parts)
        with self.pool.connection() as conn:
            rows = conn.execute("SELECT id,order_id,client_id,category,status,assigned_to,opened_by,opened_at,updated_at,version FROM operations.disputes WHERE " + where + " ORDER BY opened_at DESC,id LIMIT 25 OFFSET %(offset)s", p).fetchall()
            total = conn.execute("SELECT COUNT(*) AS n FROM operations.disputes WHERE " + where, p).fetchone()["n"]
            self.access(conn, actor, "DISPUTE_SEARCH", "cases")
            return {"rows": rows, "page": page, "pageSize": 25, "total": total}

    def detail(self, id, actor):
        with self.pool.connection() as conn:
            row = conn.execute("SELECT * FROM operations.disputes WHERE id=%s", (id,)).fetchone()
            if not row: raise HTTPException(404, "Dispute not found")
            row["events"] = conn.execute("SELECT id,actor,kind,occurred_at,details FROM operations.dispute_events WHERE dispute_id=%s ORDER BY id LIMIT 1001", (id,)).fetchall()
            row["historyTruncated"] = len(row["events"]) > 1000
            row["events"] = row["events"][:1000]
            self.access(conn, actor, "DISPUTE_VIEWED", id)
            return row

    def change(self, id, body, actor):
        with self.pool.connection() as conn:
            current = conn.execute("SELECT * FROM operations.disputes WHERE id=%s FOR UPDATE", (id,)).fetchone()
            if not current: raise HTTPException(404, "Dispute not found")
            if current["version"] != body.expectedVersion: raise HTTPException(409, "Dispute changed; reload before updating")
            state = body.status or current["status"]
            allowed = {"OPEN": {"IN_REVIEW"}, "IN_REVIEW": {"RESOLVED"}, "RESOLVED": {"CLOSED", "IN_REVIEW"}, "CLOSED": {"IN_REVIEW"}}
            if state != current["status"] and state not in allowed[current["status"]]: raise HTTPException(409, "Invalid dispute status transition")
            if state == "RESOLVED" and current["status"] != "RESOLVED" and not body.resolution: raise HTTPException(400, "Resolution is required")
            if current["status"] in ("RESOLVED", "CLOSED") and state == "IN_REVIEW" and not body.note.strip(): raise HTTPException(400, "Reopening requires a reason")
            if body.resolution and state != "RESOLVED": raise HTTPException(400, "Resolution is only valid when resolving a case")
            assignee = body.assignedTo if "assignedTo" in body.model_fields_set else current["assigned_to"]
            resolution = body.resolution or current["resolution"]
            if state == "IN_REVIEW" and current["status"] in ("RESOLVED", "CLOSED"): resolution = None
            row = conn.execute("UPDATE operations.disputes SET status=%s,assigned_to=%s,resolution=%s,updated_at=now(),version=version+1 WHERE id=%s RETURNING *", (state, assignee, resolution, id)).fetchone()
            self.case_event(conn, id, actor, "UPDATED", {"from": current["status"], "to": state, "assignedTo": assignee, "note": body.note, "resolution": body.resolution, "version": row["version"]})
            return row

    def note(self, id, text, actor):
        with self.pool.connection() as conn:
            case = conn.execute("SELECT status FROM operations.disputes WHERE id=%s FOR UPDATE", (id,)).fetchone()
            if not case: raise HTTPException(404, "Dispute not found")
            if case["status"] == "CLOSED": raise HTTPException(409, "Reopen the case before adding notes")
            self.case_event(conn, id, actor, "NOTE_ADDED", {"text": text})
            conn.execute("UPDATE operations.disputes SET updated_at=now(),version=version+1 WHERE id=%s", (id,))
            return {"added": True}

    def insights(self, f, actor):
        _, p = f.sql()
        with self.pool.connection() as conn:
            rows = conn.execute("SELECT d.id,d.status,d.category,d.opened_at,(SELECT MAX(e.occurred_at) FROM operations.dispute_events e WHERE e.dispute_id=d.id AND e.details->>'to'='RESOLVED') AS resolved_at FROM operations.disputes d WHERE d.opened_at>=%(start)s AND d.opened_at<%(end)s ORDER BY d.opened_at LIMIT 10001", p).fetchall()
            if len(rows) > 10000: raise HTTPException(400, "Narrow the date range; insights are limited to 10000 cases")
            self.access(conn, actor, "DISPUTE_INSIGHTS", str(f.from_) + "/" + str(f.to))
        return dispute_insights(rows)

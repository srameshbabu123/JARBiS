"""PostgreSQL aggregates large tables; pandas shapes the bounded daily chart dataset."""
from datetime import date
from decimal import Decimal
import pandas as pd


def chart_series(rows, start, end):
    """Fill missing UTC days without converting Decimal monetary values to floats."""
    if not rows:
        return []  # preserve the existing empty-dataset API contract
    frame = pd.DataFrame(rows)
    frame["day"] = pd.to_datetime(frame["day"])
    frame = frame.set_index("day").reindex(pd.date_range(start, end, freq="D"))
    frame.index.name = "day"
    frame["trades"] = frame["trades"].fillna(0).astype(int)
    for column in ("value", "quantity", "notional"):
        if column in frame:
            frame[column] = frame[column].map(lambda value: Decimal("0") if pd.isna(value) else value)
    return [{"day": day.date(), "trades": int(row["trades"]),
             **{key: row[key] for key in ("value", "quantity", "notional") if key in frame}}
            for day, row in frame.iterrows()]


class Reports:
    def __init__(self, pool):
        self.pool = pool

    @staticmethod
    def summary(conn, f):
        where, p = f.sql()
        result = conn.execute("SELECT COUNT(*) AS trades, COALESCE(SUM(t.quantity),0) AS value, COALESCE(SUM(t.quantity*t.execution_price),0) AS notional FROM analytics.transaction_facts t WHERE " + where, p).fetchone()
        segment = " AND " + f.client_clause("p")
        result["clients"] = conn.execute("SELECT COUNT(DISTINCT p.user_id) AS n FROM analytics.transaction_facts t JOIN analytics.trade_participants p ON p.transaction_id=t.transaction_id WHERE " + where + segment, p).fetchone()["n"]
        result["instruments"] = conn.execute("SELECT COUNT(DISTINCT t.asset_id) AS n FROM analytics.transaction_facts t WHERE " + where, p).fetchone()["n"]
        return result

    def dashboard(self, f):
        where, p = f.sql()
        with self.pool.connection() as conn:
            conn.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY")
            activity = conn.execute("SELECT (t.transaction_timestamp AT TIME ZONE 'UTC')::date AS day,COUNT(*) AS trades,SUM(t.quantity) AS value,SUM(t.quantity*t.execution_price) AS notional FROM analytics.transaction_facts t WHERE " + where + " GROUP BY 1 ORDER BY 1", p).fetchall()
            instruments = conn.execute("SELECT t.asset_id AS id,COALESCE(a.asset_name,t.ticker) AS name,COUNT(*) AS trades,SUM(t.quantity) AS value,SUM(t.quantity*t.execution_price) AS notional FROM analytics.transaction_facts t LEFT JOIN analytics.dim_assets a ON a.asset_id=t.asset_id WHERE " + where + " GROUP BY 1,2 ORDER BY value DESC,t.asset_id LIMIT 10", p).fetchall()
            clients = conn.execute("SELECT (t.transaction_timestamp AT TIME ZONE 'UTC')::date AS day,COUNT(DISTINCT p.user_id) AS clients FROM analytics.transaction_facts t JOIN analytics.trade_participants p ON p.transaction_id=t.transaction_id WHERE " + where + (" AND " + f.client_clause("p")) + " GROUP BY 1 ORDER BY 1", p).fetchall()
            ingestion = conn.execute("SELECT MAX(occurred_at) AS last_event_at,MAX(processed_at) AS last_ingested_at,COUNT(*) AS processed_events FROM analytics.processed_events").fetchone()
            return {"summary": self.summary(conn, f), "previous": self.summary(conn, f.previous()),
                    "activity": chart_series(activity, f.from_, f.to), "instruments": instruments, "clients": clients,
                    "ingestion": ingestion, "filter": f.model_dump(by_alias=True),
                    "definitions": {"value": "Total quantity of instruments traded; each matched fill counted once",
                                    "portfolio": "Holdings at purchase cost, excluding cash; per selected currency; latest snapshot on/before period end",
                                    "activity": "As of period end (UTC): active this calendar month, occasional within 90 days but not this month, dormant otherwise. All instruments/currencies within selected dataset."},
                    "portfolioCoverage": conn.execute("SELECT COUNT(DISTINCT user_id) AS clients,MAX(captured_at) AS latest_snapshot FROM analytics.client_portfolio_snapshots WHERE currency=%(currency)s AND data_source=%(dataset)s AND captured_at < %(end)s", p).fetchone()}

    def trades(self, f, page, client_id=None, order_id=None, trade_id=None):
        where, p = f.sql()
        if client_id is not None:
            where += " AND (t.buyer_user_id=%(client_id)s OR t.seller_user_id=%(client_id)s)"
            p["client_id"] = client_id
        if order_id is not None:
            where += " AND (t.buy_order_id=%(order_id)s OR t.sell_order_id=%(order_id)s)"
            p["order_id"] = order_id
        if trade_id is not None:
            where += " AND t.transaction_id=%(trade_id)s"
            p["trade_id"] = trade_id
        with self.pool.connection() as conn:
            conn.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY")
            rows = conn.execute("SELECT t.transaction_id AS id,COALESCE(a.asset_name,t.ticker) AS instrument,t.quantity,t.execution_price AS price,t.quantity AS value,t.quantity*t.execution_price AS notional,t.currency,t.transaction_timestamp AS executed_at,t.buy_order_id,t.sell_order_id,t.buyer_user_id,t.seller_user_id,t.status FROM analytics.transaction_facts t LEFT JOIN analytics.dim_assets a ON a.asset_id=t.asset_id WHERE " + where + " ORDER BY t.transaction_timestamp DESC,t.transaction_id DESC LIMIT 25 OFFSET %(offset)s", {**p, "offset": page * 25}).fetchall()
            total = conn.execute("SELECT COUNT(*) AS n FROM analytics.transaction_facts t WHERE " + where, p).fetchone()["n"]
            return {"rows": rows, "page": page, "pageSize": 25, "total": total}

    def options(self):
        with self.pool.connection() as conn:
            return {"instruments": conn.execute("SELECT asset_id AS id,asset_name AS name FROM analytics.dim_assets ORDER BY asset_name").fetchall(),
                    "segments": conn.execute("SELECT DISTINCT account_type AS name FROM analytics.trade_participants ORDER BY 1").fetchall(),
                    "currencies": conn.execute("SELECT DISTINCT currency AS name FROM analytics.transaction_facts ORDER BY 1").fetchall(),
                    "datasets": conn.execute("SELECT data_source AS name,MIN(event_date) AS first_day,MAX(event_date) AS last_day FROM analytics.transaction_facts GROUP BY 1").fetchall()}

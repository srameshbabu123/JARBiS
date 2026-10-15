"""Project committed events atomically; successful return means the DB commit succeeded."""
from datetime import timezone
from .models import SettlementEvent


def project(pool, event: SettlementEvent):
    with pool.connection() as conn:
        # Also serialize different event IDs for the same trade, across consumer instances.
        conn.execute("SELECT pg_advisory_xact_lock(%s)", (event.transactionId,))
        inserted = conn.execute("INSERT INTO analytics.processed_events(event_id,transaction_id,occurred_at) VALUES (%s,%s,%s) ON CONFLICT DO NOTHING RETURNING event_id",
                                (event.eventId, event.transactionId, event.occurredAt)).fetchone()
        if not inserted:
            return
        existing = conn.execute("SELECT data_source FROM analytics.transaction_facts WHERE transaction_id=%s", (event.transactionId,)).fetchone()
        if existing:
            if existing["data_source"] != "LIVE":
                raise ValueError("Live trade ID collides with legacy fixture; use a clean OLAP database")
            return
        conn.execute("INSERT INTO analytics.dim_assets(asset_id,ticker,asset_name,asset_type) VALUES (%s,%s,%s,%s) ON CONFLICT(asset_id) DO UPDATE SET ticker=excluded.ticker,asset_name=excluded.asset_name,asset_type=excluded.asset_type",
                     (event.assetId, event.instrument[:12], event.instrument, event.assetType))
        for p in (event.buyer, event.seller):
            conn.execute("INSERT INTO analytics.dim_users(user_id,full_name,email) VALUES (%s,'','') ON CONFLICT DO NOTHING", (p.userId,))
            conn.execute("INSERT INTO analytics.dim_accounts(account_id,user_id,account_type,currency) VALUES (%s,%s,%s,%s) ON CONFLICT(account_id) DO UPDATE SET account_type=excluded.account_type,currency=excluded.currency",
                         (p.accountId, p.userId, p.accountType, p.currency))
        conn.execute("""INSERT INTO analytics.transaction_facts(transaction_id,asset_id,ticker,buy_order_id,sell_order_id,
            buyer_account_id,seller_account_id,buyer_user_id,seller_user_id,status,quantity,execution_price,
            transaction_timestamp,event_date,currency,data_source,ingested_at)
            VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,'COMPLETED',%s,%s,%s,%s,%s,'LIVE',now())""",
                     (event.transactionId, event.assetId, event.instrument[:12], event.buyer.orderId, event.seller.orderId,
                      event.buyer.accountId, event.seller.accountId, event.buyer.userId, event.seller.userId,
                      event.quantity, event.executionPrice, event.occurredAt, event.occurredAt.astimezone(timezone.utc).date(), event.currency))
        for side, p in (("BUY", event.buyer), ("SELL", event.seller)):
            conn.execute("INSERT INTO analytics.trade_participants(transaction_id,user_id,account_id,account_type,side) VALUES (%s,%s,%s,%s,%s)",
                         (event.transactionId, p.userId, p.accountId, p.accountType, side))

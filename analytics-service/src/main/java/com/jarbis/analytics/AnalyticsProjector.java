package com.jarbis.analytics;

import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsProjector {
    private final JdbcTemplate jdbc;
    public AnalyticsProjector(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void apply(SettlementEvent e) {
        e.validate();
        int inserted = jdbc.update("INSERT INTO analytics.processed_events(event_id,transaction_id,occurred_at) VALUES (?,?,?) ON CONFLICT DO NOTHING",
            e.eventId(), e.transactionId(), Timestamp.from(e.occurredAt()));
        if (inserted == 0) return;
        // Facts are immutable. Re-delivery under a different event ID must not duplicate a trade.
        var existing = jdbc.queryForList("SELECT data_source FROM analytics.transaction_facts WHERE transaction_id=?", e.transactionId());
        if (!existing.isEmpty()) {
            if (!"LIVE".equals(existing.get(0).get("data_source"))) {
                throw new IllegalStateException("Live trade ID collides with legacy fixture; use a clean OLAP database");
            }
            return;
        }
        jdbc.update("INSERT INTO analytics.dim_assets(asset_id,ticker,asset_name,asset_type) VALUES (?,?,?,?) "
            + "ON CONFLICT(asset_id) DO UPDATE SET ticker=excluded.ticker,asset_name=excluded.asset_name,asset_type=excluded.asset_type",
            e.assetId(), ticker(e.instrument()), e.instrument(), e.assetType());
        dimension(e.buyer()); dimension(e.seller());
        jdbc.update("""
            INSERT INTO analytics.transaction_facts(transaction_id,asset_id,ticker,buy_order_id,sell_order_id,
              buyer_account_id,seller_account_id,buyer_user_id,seller_user_id,status,quantity,execution_price,
              transaction_timestamp,event_date,currency,data_source,ingested_at)
            VALUES (?,?,?,?,?,?,?,?,?,'COMPLETED',?,?,?,? ,?,'LIVE',now())
            """, e.transactionId(), e.assetId(), ticker(e.instrument()), e.buyer().orderId(), e.seller().orderId(),
            e.buyer().accountId(), e.seller().accountId(), e.buyer().userId(), e.seller().userId(), e.quantity(),
            e.executionPrice(), Timestamp.from(e.occurredAt()), e.occurredAt().atZone(java.time.ZoneOffset.UTC).toLocalDate(), e.currency());
        participant(e.transactionId(), e.buyer(), "BUY"); participant(e.transactionId(), e.seller(), "SELL");
    }
    private String ticker(String name) { return name.substring(0, Math.min(12, name.length())); }
    private void dimension(SettlementEvent.Participant p) {
        // Names/emails are not required for reporting and are not copied out of trading.
        jdbc.update("INSERT INTO analytics.dim_users(user_id,full_name,email) VALUES (?,'','') ON CONFLICT DO NOTHING", p.userId());
        jdbc.update("INSERT INTO analytics.dim_accounts(account_id,user_id,account_type,currency) VALUES (?,?,?,?) "
            + "ON CONFLICT(account_id) DO UPDATE SET account_type=excluded.account_type,currency=excluded.currency",
            p.accountId(), p.userId(), p.accountType(), p.currency());
    }
    private void participant(Long trade, SettlementEvent.Participant p, String side) {
        jdbc.update("INSERT INTO analytics.trade_participants(transaction_id,user_id,account_id,account_type,side) VALUES (?,?,?,?,?)",
            trade, p.userId(), p.accountId(), p.accountType(), side);
    }
}

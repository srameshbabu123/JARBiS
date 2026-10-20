package com.jarbis.brokerage.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;


@Component
@ConditionalOnProperty(name = "brokerage.analytics.enabled", havingValue = "true", matchIfMissing = false)
public class AnalyticsOutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsOutboxRelay.class);
    private final JdbcTemplate jdbc;
    private final OlapEventWriter writer;
    private final TransactionTemplate transactions;
    public AnalyticsOutboxRelay(JdbcTemplate jdbc, OlapEventWriter writer,
            org.springframework.transaction.PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.writer=writer; this.transactions=new TransactionTemplate(manager);
    }
    @Scheduled(fixedDelayString = "${brokerage.analytics.relay-delay-ms:2000}")
    public void publishPending() {
        // One row per transaction bounds locks and leaves failed messages available after restart.
        for (int i = 0; i < 20; i++) {
            try {
                Boolean sent = transactions.execute(status -> {
                    var rows = jdbc.queryForList("SELECT id, transaction_id, payload FROM analytics_outbox "
                        + "WHERE published_at IS NULL ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED");
                    if (rows.isEmpty()) return false;
                    var row = rows.get(0);
                    writer.write(row.get("payload").toString());
                    jdbc.update("UPDATE analytics_outbox SET published_at = now() WHERE id = ?", row.get("id"));
                    return true;
                });
                if (!Boolean.TRUE.equals(sent)) return;
            } catch (RuntimeException e) {
                log.warn("Analytics delivery delayed; event remains in outbox: {}", e.getMessage()); return;
            }
        }
    }
}

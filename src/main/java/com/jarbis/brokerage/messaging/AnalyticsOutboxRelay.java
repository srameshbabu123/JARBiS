package com.jarbis.brokerage.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "brokerage.analytics.enabled", havingValue = "true", matchIfMissing = false)
public class AnalyticsOutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsOutboxRelay.class);
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transactions;
    private final String topic;
    public AnalyticsOutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka,
            org.springframework.transaction.PlatformTransactionManager manager,
            @Value("${brokerage.analytics.topic:trading.settled.v1}") String topic) {
        this.jdbc = jdbc; this.kafka = kafka; this.transactions = new TransactionTemplate(manager); this.topic = topic;
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
                    try {
                        kafka.send(topic, row.get("transaction_id").toString(), row.get("payload").toString())
                            .get(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); throw new IllegalStateException("Relay interrupted", e);
                    } catch (Exception e) { throw new IllegalStateException("Kafka has not acknowledged settlement event", e); }
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

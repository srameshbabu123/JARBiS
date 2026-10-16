package com.jarbis.brokerage.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;

/** Bounded periodic source aggregation; dashboard requests never query trading tables. */
@Component
@ConditionalOnProperty(name="brokerage.analytics.enabled",havingValue="true")
public class PortfolioSnapshotRelay {
    private final JdbcTemplate source;
    private final OlapEventWriter target;
    private Instant lastSuccess=Instant.EPOCH;
    @org.springframework.beans.factory.annotation.Value("${brokerage.analytics.portfolio-delay-ms:3600000}")
    private long snapshotInterval=3600000;
    public PortfolioSnapshotRelay(JdbcTemplate source,OlapEventWriter target) {this.source=source;this.target=target;}
    @Scheduled(fixedDelay=60000)
    public void snapshot() {
        try {
            Instant capturedAt=Instant.now();
            if(capturedAt.isBefore(lastSuccess.plusMillis(Math.max(60000,snapshotInterval))))return;
            var rows=source.queryForList("SELECT a.owner_id AS user_id,a.currency,COALESCE(SUM(h.quantity::numeric*h.average_price::numeric),0) AS holdings_cost FROM accounts a LEFT JOIN holdings h ON h.account_id=a.id GROUP BY a.owner_id,a.currency");
            target.writePortfolios(rows,capturedAt);
            lastSuccess=capturedAt;
        } catch(RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Portfolio snapshot delayed; next scheduled run will retry");
        }
    }
}

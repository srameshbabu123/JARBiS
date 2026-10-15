package com.jarbis.analytics;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_POSTGRES_URL", matches=".*admin_dashboard_test.*")
class AnalyticsPostgresTest {
    static JdbcTemplate jdbc;
    static AnalyticsRepository repository;
    static AnalyticsProjector projector;
    static TransactionTemplate tx;
    @BeforeAll static void connect() {
        var ds=new DriverManagerDataSource(System.getenv("TEST_POSTGRES_URL"),System.getenv("TEST_POSTGRES_USER"),System.getenv("TEST_POSTGRES_PASSWORD"));
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("DROP SCHEMA IF EXISTS analytics CASCADE");
        jdbc.execute("DROP SCHEMA IF EXISTS reporting_migrations CASCADE");
        Flyway.configure().dataSource(ds).defaultSchema("reporting_migrations").load().migrate();
        repository=new AnalyticsRepository(new NamedParameterJdbcTemplate(ds));
        projector=new AnalyticsProjector(jdbc);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
    }
    @BeforeEach void clear() {
        jdbc.execute("TRUNCATE analytics.trade_participants,analytics.processed_events,analytics.transaction_facts,analytics.dim_accounts,analytics.dim_users,analytics.dim_assets CASCADE");
    }
    private SettlementEvent event(long id,String at,String currency,long seller,String instrument) {
        return new SettlementEvent(1,UUID.randomUUID(),"TradeSettled",Instant.parse(at),id,8L,instrument,"STOCK",
            BigDecimal.valueOf(id==1?2:3),BigDecimal.valueOf(id==1?10:20),currency,
            new SettlementEvent.Participant(id*2,10L,1L,"INVESTMENT",currency),
            new SettlementEvent.Participant(id*2+1,20L+seller,seller,"CHECKING",currency));
    }
    private void apply(SettlementEvent e) { tx.executeWithoutResult(s->projector.apply(e)); }
    private ReportFilter filter(String segment) { return new ReportFilter(LocalDate.of(2026,3,1),LocalDate.of(2026,3,2),null,segment,"USD","LIVE"); }
    @Test void duplicateDeliveryAndDistinctClientsUseFillGrainAndUtcBoundaries() {
        var one=event(1,"2026-03-01T00:00:00Z","USD",2,"AAPL");
        apply(one);apply(one);
        apply(new SettlementEvent(1,UUID.randomUUID(),one.type(),one.occurredAt(),one.transactionId(),one.assetId(),one.instrument(),one.assetType(),one.quantity(),one.executionPrice(),one.currency(),one.buyer(),one.seller()));
        apply(event(2,"2026-03-02T23:59:59Z","USD",3,"AAPL"));
        apply(event(3,"2026-03-03T00:00:00Z","USD",4,"AAPL"));
        apply(event(4,"2026-03-02T12:00:00Z","EUR",5,"AAPL"));
        var result=repository.summary(filter(null));
        assertEquals(2L,result.get("trades"));assertEquals(3L,result.get("clients"));
        assertEquals(0,new BigDecimal("80").compareTo((BigDecimal)result.get("value")));
        assertEquals(1L,repository.summary(filter("INVESTMENT")).get("clients"));
        assertEquals(2,repository.activity(filter(null)).size());
        assertEquals(2,repository.clients(filter(null)).size());
        assertEquals(1,repository.instruments(filter(null)).size());
        var trades=repository.trades(filter(null),0);
        assertEquals(2L,trades.get("total"));
        assertEquals(2,((List<?>)trades.get("rows")).size());
        assertFalse(repository.options().isEmpty());assertEquals(5L,repository.ingestion().get("processed_events"));
    }
    @Test void failedProjectionRollsBackDedupMarkerSoItCanBeRetried() {
        assertThrows(RuntimeException.class,()->apply(event(1,"2026-03-01T00:00:00Z","USD",2,"A".repeat(300))));
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM analytics.processed_events",Long.class));
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM analytics.transaction_facts",Long.class));
    }
    @Test void liveDatasetNeverIncludesLegacyFacts() {
        apply(event(1,"2026-03-01T00:00:00Z","USD",2,"AAPL"));
        jdbc.update("UPDATE analytics.transaction_facts SET data_source='LEGACY' WHERE transaction_id=1");
        assertEquals(0L,repository.summary(filter(null)).get("trades"));
        var f=filter(null);assertEquals(1L,repository.summary(new ReportFilter(f.from(),f.to(),null,null,"USD","LEGACY")).get("trades"));
        assertThrows(IllegalStateException.class,()->apply(event(1,"2026-03-01T00:00:00Z","USD",2,"AAPL")));
    }
}

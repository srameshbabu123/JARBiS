package com.jarbis.brokerage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarbis.brokerage.messaging.OlapEventWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_POSTGRES_URL",matches=".*admin_dashboard_test.*")
class OlapEventWriterPostgresTest {
    @Test void directWriteIsAtomicAndReplaySafe() throws Exception {
        String url=System.getenv("TEST_POSTGRES_URL"); if(!url.startsWith("jdbc:"))url="jdbc:"+url;
        String user=System.getenv("TEST_POSTGRES_USER"), password=System.getenv("TEST_POSTGRES_PASSWORD");
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        assertEquals("admin_dashboard_test",jdbc.queryForObject("SELECT current_database()",String.class));
        jdbc.execute("DROP SCHEMA IF EXISTS operations CASCADE; DROP SCHEMA IF EXISTS analytics CASCADE");
        try(var paths=Files.list(Path.of("analytics-service/migrations"))) {
            for(var path:paths.sorted().toList())jdbc.execute(Files.readString(path));
        }
        var writer=new OlapEventWriter(new ObjectMapper(),url,user,password);
        String payload="""
            {"schemaVersion":1,"eventId":"11111111-1111-1111-1111-111111111111","type":"TradeSettled",
            "occurredAt":"2026-03-01T00:00:00Z","transactionId":987654,"assetId":8,"instrument":"AAPL",
            "assetType":"STOCK","quantity":"2","executionPrice":"10","currency":"USD",
            "buyer":{"orderId":2,"accountId":10,"userId":1,"accountType":"INVESTMENT","currency":"USD"},
            "seller":{"orderId":3,"accountId":20,"userId":2,"accountType":"CHECKING","currency":"USD"}}
            """;
        writer.write(payload); writer.write(payload);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM analytics.transaction_facts WHERE transaction_id=987654",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM analytics.trade_participants WHERE transaction_id=987654",Integer.class));
        String broken=payload.replace("11111111-1111-1111-1111-111111111111","22222222-2222-2222-2222-222222222222").replace("987654","987655").replace("\"quantity\":\"2\"","\"quantity\":\"invalid\"");
        assertThrows(IllegalStateException.class,()->writer.write(broken));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM analytics.processed_events WHERE transaction_id=987655",Integer.class));
        String lifecycle="""
            {"eventId":"33333333-3333-3333-3333-333333333333","type":"OrderLifecycle","auditSequence":1,
            "orderId":2,"clientId":1,"accountId":10,"assetId":8,"eventType":"ORDER_SUBMITTED","occurredAt":"2026-03-01T00:00:00Z"}
            """;
        writer.write(lifecycle); writer.write(lifecycle);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM analytics.order_events",Integer.class));
    }
}

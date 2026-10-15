package com.jarbis.brokerage.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.jarbis.brokerage.config.OrderAuditProtection;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_POSTGRES_URL",matches=".*admin_dashboard_test.*")
class OrderAuditPostgresTest {
    @Test void immutableEvidenceAllowsOnlyDeliveryMetadataChanges() {
        String url=System.getenv("TEST_POSTGRES_URL");if(!url.startsWith("jdbc:"))url="jdbc:"+url;
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,System.getenv("TEST_POSTGRES_USER"),System.getenv("TEST_POSTGRES_PASSWORD")));
        assertEquals("admin_dashboard_test",jdbc.queryForObject("SELECT current_database()",String.class));
        jdbc.execute("DROP TABLE IF EXISTS order_audit_outbox CASCADE");
        jdbc.execute("CREATE TABLE order_audit_outbox(sequence BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,event_id UUID,order_id BIGINT,payload TEXT,occurred_at TIMESTAMPTZ,published_at TIMESTAMPTZ)");
        new OrderAuditProtection(jdbc).run(null);
        jdbc.update("INSERT INTO order_audit_outbox(event_id,order_id,payload,occurred_at) VALUES (gen_random_uuid(),1,'original',now())");
        jdbc.update("UPDATE order_audit_outbox SET published_at=now()");
        assertThrows(RuntimeException.class,()->jdbc.update("UPDATE order_audit_outbox SET payload='edited'"));
        assertThrows(RuntimeException.class,()->jdbc.update("DELETE FROM order_audit_outbox"));
        assertEquals("original",jdbc.queryForObject("SELECT payload FROM order_audit_outbox",String.class));
    }
}

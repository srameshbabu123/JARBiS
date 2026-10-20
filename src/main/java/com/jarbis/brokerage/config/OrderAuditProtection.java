package com.jarbis.brokerage.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** PostgreSQL rejects edits/deletes of source evidence; delivery metadata may change. */
@Component
public class OrderAuditProtection implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public OrderAuditProtection(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public void run(ApplicationArguments args) {
        jdbc.execute("""
            CREATE OR REPLACE FUNCTION preserve_order_audit() RETURNS trigger LANGUAGE plpgsql AS $$
            BEGIN
              IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Order audit evidence is append-only'; END IF;
              IF (to_jsonb(NEW)-'published_at') IS DISTINCT FROM (to_jsonb(OLD)-'published_at') THEN
                RAISE EXCEPTION 'Order audit evidence is append-only';
              END IF;
              RETURN NEW;
            END $$;
            DROP TRIGGER IF EXISTS preserve_order_audit_trigger ON order_audit_outbox;
            CREATE TRIGGER preserve_order_audit_trigger BEFORE UPDATE OR DELETE ON order_audit_outbox
              FOR EACH ROW EXECUTE FUNCTION preserve_order_audit();
            """);
    }
}

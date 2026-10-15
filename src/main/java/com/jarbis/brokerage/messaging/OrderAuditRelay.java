package com.jarbis.brokerage.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name="brokerage.analytics.enabled",havingValue="true")
public class OrderAuditRelay {
    private final JdbcTemplate jdbc;private final KafkaTemplate<String,String> kafka;
    private final ObjectMapper mapper;private final TransactionTemplate tx;
    public OrderAuditRelay(JdbcTemplate jdbc,KafkaTemplate<String,String> kafka,ObjectMapper mapper,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.kafka=kafka;this.mapper=mapper;tx=new TransactionTemplate(manager);
    }
    @Scheduled(fixedDelayString="${brokerage.analytics.relay-delay-ms:2000}")
    public void publish() {
        for(int i=0;i<20;i++) {
            try {
                Boolean sent=tx.execute(status->{
                    var rows=jdbc.queryForList("SELECT sequence,order_id,payload FROM order_audit_outbox WHERE published_at IS NULL ORDER BY sequence LIMIT 1 FOR UPDATE SKIP LOCKED");
                    if(rows.isEmpty())return false;
                    var row=rows.get(0);
                    try {
                        ObjectNode event=(ObjectNode)mapper.readTree(row.get("payload").toString());
                        event.put("auditSequence",((Number)row.get("sequence")).longValue());
                        kafka.send("trading.order-lifecycle.v1",row.get("order_id").toString(),mapper.writeValueAsString(event)).get(10,TimeUnit.SECONDS);
                    } catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException("Audit relay interrupted",e); }
                    catch(Exception e) { throw new IllegalStateException("Audit event not acknowledged",e); }
                    jdbc.update("UPDATE order_audit_outbox SET published_at=now() WHERE sequence=?",row.get("sequence"));return true;
                });
                if(!Boolean.TRUE.equals(sent))return;
            } catch(RuntimeException e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Order audit delivery delayed; durable row remains pending");return; }
        }
    }
}

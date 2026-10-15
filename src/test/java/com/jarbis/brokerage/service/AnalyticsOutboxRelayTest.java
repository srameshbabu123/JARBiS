package com.jarbis.brokerage.service;

import com.jarbis.brokerage.messaging.AnalyticsOutboxRelay;
import com.jarbis.brokerage.messaging.OlapEventWriter;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;

class AnalyticsOutboxRelayTest {
    @Test void deliveryIsMarkedOnlyAfterOlapCommitReturns() {
        var jdbc=mock(JdbcTemplate.class);
        var writer=mock(OlapEventWriter.class);
        var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("id","event","transaction_id",1,"payload","{}")),List.of());
        new AnalyticsOutboxRelay(jdbc,writer,manager).publishPending();
        var order=inOrder(writer,jdbc);
        order.verify(writer).write("{}");
        order.verify(jdbc).update(anyString(),eq("event"));
        verify(manager,times(2)).commit(any());
    }
    @Test void olapFailureLeavesSourcePending() {
        var jdbc=mock(JdbcTemplate.class);
        var writer=mock(OlapEventWriter.class);
        var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("id","event","transaction_id",1,"payload","{}")));
        doThrow(new IllegalStateException("offline")).when(writer).write(anyString());
        new AnalyticsOutboxRelay(jdbc,writer,manager).publishPending();
        verify(jdbc,never()).update(anyString(),any(Object.class));
        verify(manager).rollback(any());
    }
}

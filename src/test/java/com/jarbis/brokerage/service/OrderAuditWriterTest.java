package com.jarbis.brokerage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarbis.brokerage.entity.*;
import com.jarbis.brokerage.enums.*;
import com.jarbis.brokerage.messaging.OrderAuditWriter;
import com.jarbis.brokerage.repository.OrderAuditRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderAuditWriterTest {
    @Test void capturesFinancialSnapshotAndActualPricingBasis() throws Exception {
        var repo=mock(OrderAuditRepository.class);var mapper=new ObjectMapper().findAndRegisterModules();
        var writer=new OrderAuditWriter(repo,mapper,mock(PlatformTransactionManager.class));
        var order=mock(Order.class);var account=mock(Account.class);var user=mock(User.class);var asset=mock(Asset.class);
        when(order.getId()).thenReturn(1L);when(order.getAccount()).thenReturn(account);when(account.getOwner()).thenReturn(user);
        when(user.getId()).thenReturn(2L);when(account.getId()).thenReturn(3L);when(account.getCurrency()).thenReturn(com.jarbis.brokerage.enums.Currency.USD);
        when(account.getBalance()).thenReturn(1000.0);when(account.getHoldings()).thenReturn(List.of());
        when(order.getAsset()).thenReturn(asset);when(asset.getId()).thenReturn(4L);when(asset.getName()).thenReturn("AAPL");
        when(order.getSide()).thenReturn(OrderSide.BUY);when(order.getStatus()).thenReturn(OrderStatus.PENDING);
        when(order.getQuantity()).thenReturn(2.0);when(order.getRemainingQuantity()).thenReturn(2.0);when(order.getPrice()).thenReturn(100.0);
        writer.record(order,"EXECUTION_PRICED",Map.of("priceBasis","RESTING_ORDER_PRICE","marketQuoteRecorded",false));
        var capture=ArgumentCaptor.forClass(OrderAuditEvent.class);verify(repo).save(capture.capture());
        var event=mapper.readTree((String)ReflectionTestUtils.getField(capture.getValue(),"payload"));
        assertEquals(1000.0,event.get("cashBalance").asDouble());assertEquals(2,event.get("clientId").asInt());
        assertFalse(event.get("details").get("marketQuoteRecorded").asBoolean());
        assertFalse(event.toString().contains("password"));
    }
    @Test void failureEvidenceIsWrittenOnlyAfterRollbackInAnIndependentTransaction() {
        var repo=mock(OrderAuditRepository.class);var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        var writer=new OrderAuditWriter(repo,new ObjectMapper().findAndRegisterModules(),manager);
        TransactionSynchronizationManager.initSynchronization();
        try {
            writer.failureAfterRollback(List.of(Map.of("orderId",1L)),"InsufficientBalanceException");
            verifyNoInteractions(repo);
            var callback=TransactionSynchronizationManager.getSynchronizations().get(0);
            callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);verifyNoInteractions(repo);
            callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);verify(repo).save(any());verify(manager).commit(any());
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}

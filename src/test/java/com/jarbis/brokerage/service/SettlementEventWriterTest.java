package com.jarbis.brokerage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarbis.brokerage.entity.*;
import com.jarbis.brokerage.enums.*;
import com.jarbis.brokerage.messaging.SettlementEventWriter;
import com.jarbis.brokerage.repository.AnalyticsOutboxRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettlementEventWriterTest {
    @Test void recordsImmutableSettlementSnapshotWithoutClientPersonalData() throws Exception {
        var repository=mock(AnalyticsOutboxRepository.class);
        var mapper=new ObjectMapper().findAndRegisterModules();
        var trade=mock(Transaction.class);var asset=mock(Asset.class);
        var buyer=mock(Order.class);var seller=mock(Order.class);
        when(trade.getId()).thenReturn(9L);when(trade.getExecutedAt()).thenReturn(Instant.parse("2026-10-15T03:00:00Z"));
        when(trade.getAsset()).thenReturn(asset);when(asset.getId()).thenReturn(8L);when(asset.getName()).thenReturn("AAPL");when(asset.getType()).thenReturn(AssetType.STOCK);
        when(trade.getQuantity()).thenReturn(2.5);when(trade.getExecutionPrice()).thenReturn(100.0);
        when(trade.getBuyOrder()).thenReturn(buyer);when(trade.getSellOrder()).thenReturn(seller);
        participant(buyer,1L,Currency.USD);participant(seller,2L,Currency.EUR);
        new SettlementEventWriter(repository,mapper).record(trade);
        var capture=ArgumentCaptor.forClass(AnalyticsOutbox.class);verify(repository).save(capture.capture());
        var event=mapper.readTree((String)ReflectionTestUtils.getField(capture.getValue(),"payload"));
        assertEquals("TradeSettled",event.get("type").asText());
        assertEquals(9,event.get("transactionId").asLong());
        assertEquals("UNKNOWN",event.get("currency").asText());
        assertEquals(2.5,event.get("quantity").asDouble());
        assertFalse(event.toString().contains("password"));assertFalse(event.toString().contains("email"));
    }
    private void participant(Order order,Long id,Currency currency) {
        var account=mock(Account.class);var user=mock(User.class);
        when(order.getId()).thenReturn(id);when(order.getAccount()).thenReturn(account);
        when(account.getId()).thenReturn(id);when(account.getOwner()).thenReturn(user);
        when(user.getId()).thenReturn(id);when(account.getAccountType()).thenReturn(AccountType.INVESTMENT);
        when(account.getCurrency()).thenReturn(currency);
    }
    @Test void anAlreadyRecordedTradeDoesNotGenerateAnotherEvent() {
        var repository=mock(AnalyticsOutboxRepository.class);var trade=mock(Transaction.class);
        when(trade.getId()).thenReturn(1L);when(repository.existsByTransactionId(1L)).thenReturn(true);
        new SettlementEventWriter(repository,new ObjectMapper()).record(trade);
        verify(repository,never()).save(any());
    }
}

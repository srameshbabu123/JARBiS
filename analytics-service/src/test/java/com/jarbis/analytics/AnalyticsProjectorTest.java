package com.jarbis.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalyticsProjectorTest {
    private SettlementEvent event() {
        var buyer=new SettlementEvent.Participant(1L,2L,3L,"CASH","USD");
        var seller=new SettlementEvent.Participant(4L,5L,6L,"CASH","USD");
        return new SettlementEvent(1,UUID.randomUUID(),"TradeSettled",Instant.now(),7L,8L,"AAPL","STOCK",new BigDecimal("2.5"),new BigDecimal("200"),"USD",buyer,seller);
    }
    @Test void duplicateEventDoesNotWriteFacts() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class); var e=event();
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(0);
        new AnalyticsProjector(jdbc).apply(e);
        verify(jdbc,times(1)).update(anyString(),any(Object[].class));
        verifyNoMoreInteractions(jdbc);
    }
    @Test void refusesToSilentlyReplaceLegacySampleWithLiveTrade() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("data_source","LEGACY")));
        assertThrows(IllegalStateException.class,()->new AnalyticsProjector(jdbc).apply(event()));
    }
    @Test void malformedFinancialEventIsRejectedBeforeDatabaseWrites() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        var e=event();
        var invalid=new SettlementEvent(2,e.eventId(),e.type(),e.occurredAt(),e.transactionId(),e.assetId(),e.instrument(),e.assetType(),e.quantity(),e.executionPrice(),e.currency(),e.buyer(),e.seller());
        assertThrows(IllegalArgumentException.class,()->new AnalyticsProjector(jdbc).apply(invalid));
        verifyNoInteractions(jdbc);
    }
}

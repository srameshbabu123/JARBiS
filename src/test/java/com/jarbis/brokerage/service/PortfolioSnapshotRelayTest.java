package com.jarbis.brokerage.service;

import com.jarbis.brokerage.messaging.OlapEventWriter;
import com.jarbis.brokerage.messaging.PortfolioSnapshotRelay;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;

class PortfolioSnapshotRelayTest {
    @Test void retriesFailedSnapshotButDoesNotReaggregateAfterSuccess() {
        var source=mock(JdbcTemplate.class);
        var target=mock(OlapEventWriter.class);
        var rows=List.<Map<String,Object>>of(Map.of("user_id",1L,"currency","USD","holdings_cost",10000));
        when(source.queryForList(anyString())).thenReturn(rows);
        doThrow(new IllegalStateException("database down")).doNothing().when(target).writePortfolios(eq(rows),any());
        var relay=new PortfolioSnapshotRelay(source,target);
        relay.snapshot();relay.snapshot();relay.snapshot();
        verify(source,times(2)).queryForList(contains("GROUP BY a.owner_id,a.currency"));
        verify(target,times(2)).writePortfolios(eq(rows),any());
    }
}

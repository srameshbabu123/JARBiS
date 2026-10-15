package com.jarbis.analytics;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class ReportFilterTest {
    @Test void rejectsUnboundedRangesAndUnsafeFilterValues() {
        var start=LocalDate.of(2026,1,1);
        assertThrows(RuntimeException.class,()->new ReportFilter(start,start.minusDays(1),null,null,"USD","LIVE"));
        assertThrows(RuntimeException.class,()->new ReportFilter(start,start.plusDays(367),null,null,"USD","LIVE"));
        assertThrows(RuntimeException.class,()->new ReportFilter(start,start,null,"x' OR 1=1","USD","LIVE"));
        assertThrows(RuntimeException.class,()->new ReportFilter(start,start,null,null,"USD","ALL"));
    }
    @Test void comparisonUsesAdjacentEqualLengthPeriodAcrossMonthBoundary() {
        var f=new ReportFilter(LocalDate.of(2026,3,1),LocalDate.of(2026,3,30),12L,"CASH","USD","LIVE");
        assertEquals(LocalDate.of(2026,1,30),f.previous().from());
        assertEquals(LocalDate.of(2026,2,28),f.previous().to());
        assertEquals(f.instrumentId(),f.previous().instrumentId());
        assertEquals(f.segment(),f.previous().segment());
    }
}

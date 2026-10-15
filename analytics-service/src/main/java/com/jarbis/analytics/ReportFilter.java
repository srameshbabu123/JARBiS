package com.jarbis.analytics;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record ReportFilter(LocalDate from, LocalDate to, Long instrumentId, String segment, String currency, String dataset) {
    public ReportFilter {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 366
                || currency == null || !currency.matches("[A-Z]{3,10}")
                || !("LIVE".equals(dataset) || "LEGACY".equals(dataset))
                || (segment != null && (segment.length() > 50 || !segment.matches("[A-Z_]+"))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a valid date range of at most 367 days, currency, and dataset");
    }
    public ReportFilter previous() {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        return new ReportFilter(from.minusDays(days), from.minusDays(1), instrumentId, segment, currency, dataset);
    }
}

package com.jarbis.analytics;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/admin/analytics")
public class AnalyticsController {
    private final AnalyticsRepository repository;
    public AnalyticsController(AnalyticsRepository repository) { this.repository = repository; }
    @GetMapping("/options")
    public Map<String,Object> options() { return repository.options(); }
    @org.springframework.transaction.annotation.Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String,Object>> dashboard(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) Long instrumentId, @RequestParam(required=false) String segment,
            @RequestParam(defaultValue="USD") String currency, @RequestParam(defaultValue="LIVE") String dataset) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        ReportFilter f = new ReportFilter(from == null ? end.minusDays(29) : from, end, instrumentId, segment, currency, dataset);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
            "summary", repository.summary(f), "previous", repository.summary(f.previous()),
            "activity", repository.activity(f), "instruments", repository.instruments(f), "clients", repository.clients(f),
            "ingestion", repository.ingestion(), "filter", f));
    }
    @GetMapping("/trades")
    public Map<String,Object> trades(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) Long instrumentId, @RequestParam(required=false) String segment,
            @RequestParam(defaultValue="USD") String currency, @RequestParam(defaultValue="LIVE") String dataset,
            @RequestParam(defaultValue="0") int page) {
        if (page < 0 || page > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid page");
        return repository.trades(new ReportFilter(from,to,instrumentId,segment,currency,dataset), page);
    }
}

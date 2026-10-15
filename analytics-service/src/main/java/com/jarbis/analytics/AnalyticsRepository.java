package com.jarbis.analytics;

import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalyticsRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public AnalyticsRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private String where(ReportFilter f) {
        return " t.status='COMPLETED' AND t.transaction_timestamp>=:start AND t.transaction_timestamp<:end "
            + "AND t.currency=:currency AND t.data_source=:dataset "
            + (f.instrumentId() != null ? " AND t.asset_id=:instrument " : "")
            + (f.segment() != null ? " AND EXISTS (SELECT 1 FROM analytics.trade_participants s WHERE s.transaction_id=t.transaction_id AND s.account_type=:segment) " : "");
    }
    private MapSqlParameterSource params(ReportFilter f) {
        return new MapSqlParameterSource().addValue("start", Timestamp.from(f.from().atStartOfDay(ZoneOffset.UTC).toInstant()))
            .addValue("end", Timestamp.from(f.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()))
            .addValue("currency", f.currency()).addValue("dataset", f.dataset())
            .addValue("instrument", f.instrumentId()).addValue("segment", f.segment());
    }
    public Map<String, Object> summary(ReportFilter f) {
        Map<String,Object> result = new LinkedHashMap<>(jdbc.queryForMap("SELECT COUNT(*) AS trades, "
            + "COALESCE(SUM(t.quantity*t.execution_price),0) AS value FROM analytics.transaction_facts t WHERE " + where(f), params(f)));
        result.put("clients", jdbc.queryForObject("SELECT COUNT(DISTINCT p.user_id) FROM analytics.transaction_facts t "
            + "JOIN analytics.trade_participants p ON p.transaction_id=t.transaction_id WHERE " + where(f)
            + (f.segment() != null ? " AND p.account_type=:segment" : ""), params(f), Long.class));
        result.put("instruments", jdbc.queryForObject("SELECT COUNT(DISTINCT t.asset_id) FROM analytics.transaction_facts t WHERE " + where(f), params(f), Long.class));
        return result;
    }
    public List<Map<String,Object>> activity(ReportFilter f) {
        return jdbc.queryForList("SELECT (t.transaction_timestamp AT TIME ZONE 'UTC')::date AS day, COUNT(*) AS trades, "
            + "SUM(t.quantity*t.execution_price) AS value FROM analytics.transaction_facts t WHERE " + where(f)
            + " GROUP BY 1 ORDER BY 1", params(f));
    }
    public List<Map<String,Object>> instruments(ReportFilter f) {
        return jdbc.queryForList("SELECT t.asset_id AS id, COALESCE(a.asset_name,t.ticker) AS name, COUNT(*) AS trades, "
            + "SUM(t.quantity*t.execution_price) AS value FROM analytics.transaction_facts t LEFT JOIN analytics.dim_assets a ON a.asset_id=t.asset_id WHERE "
            + where(f) + " GROUP BY 1,2 ORDER BY value DESC,t.asset_id LIMIT 10", params(f));
    }
    public List<Map<String,Object>> clients(ReportFilter f) {
        return jdbc.queryForList("SELECT (t.transaction_timestamp AT TIME ZONE 'UTC')::date AS day, COUNT(DISTINCT p.user_id) AS clients "
            + "FROM analytics.transaction_facts t JOIN analytics.trade_participants p ON p.transaction_id=t.transaction_id WHERE "
            + where(f) + (f.segment() != null ? " AND p.account_type=:segment" : "") + " GROUP BY 1 ORDER BY 1", params(f));
    }
    public Map<String,Object> trades(ReportFilter f, int page) {
        var p = params(f).addValue("offset", page * 25);
        var rows = jdbc.queryForList("SELECT t.transaction_id AS id, COALESCE(a.asset_name,t.ticker) AS instrument,t.quantity,t.execution_price AS price, "
            + "t.quantity*t.execution_price AS value,t.currency,t.transaction_timestamp AS executed_at,t.buy_order_id,t.sell_order_id "
            + "FROM analytics.transaction_facts t LEFT JOIN analytics.dim_assets a ON a.asset_id=t.asset_id WHERE " + where(f)
            + " ORDER BY t.transaction_timestamp DESC,t.transaction_id DESC LIMIT 25 OFFSET :offset", p);
        return Map.of("rows", rows, "page", page, "pageSize", 25,
            "total", jdbc.queryForObject("SELECT COUNT(*) FROM analytics.transaction_facts t WHERE " + where(f), p, Long.class));
    }
    public Map<String,Object> options() {
        return Map.of("instruments", jdbc.queryForList("SELECT asset_id AS id,asset_name AS name FROM analytics.dim_assets ORDER BY asset_name", Map.of()),
            "segments", jdbc.queryForList("SELECT DISTINCT account_type AS name FROM analytics.trade_participants ORDER BY 1", Map.of()),
            "currencies", jdbc.queryForList("SELECT DISTINCT currency AS name FROM analytics.transaction_facts ORDER BY 1", Map.of()),
            "datasets", jdbc.queryForList("SELECT data_source AS name,MIN(event_date) AS first_day,MAX(event_date) AS last_day FROM analytics.transaction_facts GROUP BY 1", Map.of()));
    }
    public Map<String,Object> ingestion() {
        return jdbc.queryForMap("SELECT MAX(occurred_at) AS last_event_at,MAX(processed_at) AS last_ingested_at,COUNT(*) AS processed_events FROM analytics.processed_events", Map.of());
    }
}

package com.jarbis.brokerage.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Commits OLAP independently before the source outbox row is marked delivered. */
@Component
@ConditionalOnProperty(name="brokerage.analytics.enabled", havingValue="true")
public class OlapEventWriter {
    private final String url, user, password;
    private final ObjectMapper mapper;
    public OlapEventWriter(ObjectMapper mapper,
            @Value("${brokerage.analytics.db-url:jdbc:postgresql://localhost:8091/jarbis_olap}") String url,
            @Value("${brokerage.analytics.db-user:jarbis_olap_user}") String user,
            @Value("${brokerage.analytics.db-password}") String password) {
        this.mapper=mapper; this.url=url; this.user=user; this.password=password;
    }
    public void writePortfolios(java.util.List<java.util.Map<String,Object>> rows, java.time.Instant capturedAt) {
        var properties=new java.util.Properties();
        properties.setProperty("user",user); properties.setProperty("password",password);
        properties.setProperty("connectTimeout","5"); properties.setProperty("socketTimeout","15");
        try(var connection=DriverManager.getConnection(url,properties)) {
            connection.setAutoCommit(false);
            try {
                var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
                jdbc.setQueryTimeout(10);
                for(var row:rows) jdbc.update("INSERT INTO analytics.client_portfolio_snapshots(user_id,currency,captured_at,holdings_cost) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",
                    row.get("user_id"),row.get("currency"),java.sql.Timestamp.from(capturedAt),row.get("holdings_cost"));
                connection.commit();
            } catch(Exception failure) {connection.rollback();throw failure;}
        } catch(Exception failure) {throw new IllegalStateException("Portfolio snapshot delivery failed",failure);}
    }
    public void write(String payload) {
        var properties=new java.util.Properties();
        properties.setProperty("user",user); properties.setProperty("password",password);
        properties.setProperty("connectTimeout","5"); properties.setProperty("socketTimeout","15");
        try (var connection=DriverManager.getConnection(url,properties)) {
            connection.setAutoCommit(false);
            try {
                var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
                jdbc.setQueryTimeout(10);
                JsonNode e=mapper.readTree(payload);
                UUID id=UUID.fromString(e.required("eventId").asText());
                OffsetDateTime time=OffsetDateTime.parse(e.required("occurredAt").asText());
                if ("OrderLifecycle".equals(e.required("type").asText())) {
                    jdbc.update("INSERT INTO analytics.order_events(event_id,audit_sequence,order_id,client_id,account_id,asset_id,event_type,occurred_at,payload) VALUES (?,?,?,?,?,?,?,?,?::jsonb) ON CONFLICT(event_id) DO NOTHING",
                        id,e.required("auditSequence").asLong(),e.required("orderId").asLong(),e.required("clientId").asLong(),e.required("accountId").asLong(),e.required("assetId").asLong(),e.required("eventType").asText(),time,payload);
                } else if ("TradeSettled".equals(e.required("type").asText())) {
                    settlement(jdbc,e,id,time);
                } else throw new IllegalArgumentException("Unsupported OLAP event type");
                connection.commit();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        } catch (Exception failure) { throw new IllegalStateException("OLAP delivery failed; source event must remain pending",failure); }
    }
    private void settlement(JdbcTemplate j,JsonNode e,UUID id,OffsetDateTime time) {
        long trade=e.required("transactionId").asLong();
        j.execute("SET LOCAL TIME ZONE 'UTC'");
        j.queryForList("SELECT pg_advisory_xact_lock(?)",trade);
        int added=j.update("INSERT INTO analytics.processed_events(event_id,transaction_id,occurred_at) VALUES (?,?,?) ON CONFLICT DO NOTHING",id,trade,time);
        if(added==0)return;
        var existing=j.queryForList("SELECT data_source FROM analytics.transaction_facts WHERE transaction_id=?",trade);
        if(!existing.isEmpty()) {
            if(!"LIVE".equals(existing.get(0).get("data_source")))throw new IllegalStateException("Live trade collides with legacy data");
            return;
        }
        String instrument=e.required("instrument").asText();
        String ticker=instrument.substring(0,Math.min(12,instrument.length()));
        long asset=e.required("assetId").asLong();
        j.update("INSERT INTO analytics.dim_assets(asset_id,ticker,asset_name,asset_type) VALUES (?,?,?,?) ON CONFLICT(asset_id) DO UPDATE SET ticker=excluded.ticker,asset_name=excluded.asset_name,asset_type=excluded.asset_type",asset,ticker,instrument,e.required("assetType").asText());
        JsonNode buyer=e.required("buyer"),seller=e.required("seller");
        for(JsonNode participant:new JsonNode[]{buyer,seller}) {
            j.update("INSERT INTO analytics.dim_users(user_id,full_name,email) VALUES (?,?,?) ON CONFLICT(user_id) DO UPDATE SET full_name=COALESCE(NULLIF(excluded.full_name,''),analytics.dim_users.full_name),email=COALESCE(NULLIF(excluded.email,''),analytics.dim_users.email)",
                participant.required("userId").asLong(),participant.path("fullName").asText(""),participant.path("email").asText(""));
            j.update("INSERT INTO analytics.dim_accounts(account_id,user_id,account_type,currency) VALUES (?,?,?,?) ON CONFLICT(account_id) DO UPDATE SET account_type=excluded.account_type,currency=excluded.currency",participant.required("accountId").asLong(),participant.required("userId").asLong(),participant.required("accountType").asText(),participant.required("currency").asText());
        }
        j.update("INSERT INTO analytics.transaction_facts(transaction_id,asset_id,ticker,buy_order_id,sell_order_id,buyer_account_id,seller_account_id,buyer_user_id,seller_user_id,status,quantity,execution_price,transaction_timestamp,event_date,currency,data_source,ingested_at) VALUES (?,?,?,?,?,?,?,?,?,'COMPLETED',?,?,?,?,?,'LIVE',now())",
            trade,asset,ticker,buyer.required("orderId").asLong(),seller.required("orderId").asLong(),buyer.required("accountId").asLong(),seller.required("accountId").asLong(),buyer.required("userId").asLong(),seller.required("userId").asLong(),new java.math.BigDecimal(e.required("quantity").asText()),new java.math.BigDecimal(e.required("executionPrice").asText()),time,java.sql.Date.valueOf(time.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate()),e.required("currency").asText());
        for(JsonNode participant:new JsonNode[]{buyer,seller}) {
            j.update("INSERT INTO analytics.trade_participants(transaction_id,user_id,account_id,account_type,side) VALUES (?,?,?,?,?)",trade,participant.required("userId").asLong(),participant.required("accountId").asLong(),participant.required("accountType").asText(),participant==buyer?"BUY":"SELL");
        }
    }
}

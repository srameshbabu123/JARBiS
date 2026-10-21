package com.jarbis.brokerage.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarbis.brokerage.entity.*;
import com.jarbis.brokerage.repository.AnalyticsOutboxRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Component
public class SettlementEventWriter {
    private final AnalyticsOutboxRepository repository;
    private final ObjectMapper mapper;
    public SettlementEventWriter(AnalyticsOutboxRepository repository, ObjectMapper mapper) {
        this.repository = repository; this.mapper = mapper;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Transaction trade) {
        if (repository.existsByTransactionId(trade.getId())) return;
        UUID eventId = UUID.randomUUID();
        Instant at = trade.getExecutedAt();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("schemaVersion", 1); event.put("eventId", eventId); event.put("type", "TradeSettled");
        event.put("occurredAt", at); event.put("transactionId", trade.getId());
        event.put("assetId", trade.getAsset().getId()); event.put("instrument", trade.getAsset().getName());
        event.put("assetType", trade.getAsset().getType().name());
        event.put("quantity", BigDecimal.valueOf(trade.getQuantity()));
        event.put("executionPrice", BigDecimal.valueOf(trade.getExecutionPrice()));
        event.put("buyer", participant(trade.getBuyOrder())); event.put("seller", participant(trade.getSellOrder()));
        // Cross-currency settlement is not converted by the existing engine. Never report it as a valid currency total.
        String buyCurrency = trade.getBuyOrder().getAccount().getCurrency().name();
        String sellCurrency = trade.getSellOrder().getAccount().getCurrency().name();
        event.put("currency", buyCurrency.equals(sellCurrency) ? buyCurrency : "UNKNOWN");
        try {
            repository.save(new AnalyticsOutbox(eventId, trade.getId(), mapper.writeValueAsString(event), at));
        } catch (JsonProcessingException e) { throw new IllegalStateException("Cannot record settlement event", e); }
    }
    private Map<String, Object> participant(Order order) {
        Account account = order.getAccount();
        Map<String, Object> result = new LinkedHashMap<>(Map.of("orderId", order.getId(), "accountId", account.getId(),
            "userId", account.getOwner().getId(), "accountType", account.getAccountType().name(), "currency", account.getCurrency().name()));
        if (account.getOwner().getFullName() != null) result.put("fullName", account.getOwner().getFullName());
        if (account.getOwner().getEmail() != null) result.put("email", account.getOwner().getEmail());
        return result;
    }
}

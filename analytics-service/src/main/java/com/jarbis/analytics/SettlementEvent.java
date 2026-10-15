package com.jarbis.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SettlementEvent(int schemaVersion, UUID eventId, String type, Instant occurredAt,
        Long transactionId, Long assetId, String instrument, String assetType,
        BigDecimal quantity, BigDecimal executionPrice, String currency, Participant buyer, Participant seller) {
    public record Participant(Long orderId, Long accountId, Long userId, String accountType, String currency) { }
    public void validate() {
        if (schemaVersion != 1 || !"TradeSettled".equals(type) || eventId == null || occurredAt == null
                || transactionId == null || assetId == null || instrument == null || instrument.isBlank()
                || assetType == null || currency == null || quantity == null || quantity.signum() <= 0
                || executionPrice == null || executionPrice.signum() <= 0) {
            throw new IllegalArgumentException("Invalid or unsupported settlement event");
        }
        validateParticipant(buyer); validateParticipant(seller);
    }
    private static void validateParticipant(Participant p) {
        if (p == null || p.orderId() == null || p.accountId() == null || p.userId() == null
                || p.accountType() == null || p.currency() == null) throw new IllegalArgumentException("Missing trade participant");
    }
}

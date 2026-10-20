package com.jarbis.brokerage.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A committed settlement payload; retained after delivery for replay/reconciliation. */
@Entity
@Table(name = "analytics_outbox", indexes = @Index(name = "analytics_outbox_pending_idx", columnList = "published_at,created_at"))
public class AnalyticsOutbox {
    @Id private UUID id;
    @Column(name = "transaction_id", nullable = false, unique = true) private Long transactionId;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "published_at") private Instant publishedAt;
    protected AnalyticsOutbox() { }
    public AnalyticsOutbox(UUID id, Long transactionId, String payload, Instant createdAt) {
        this.id = id; this.transactionId = transactionId; this.payload = payload; this.createdAt = createdAt;
    }
}

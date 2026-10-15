package com.jarbis.brokerage.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Independent evidence/outbox row: no cascading relationship to mutable orders. */
@Entity
@Table(name="order_audit_outbox", indexes=@Index(name="order_audit_pending_idx",columnList="published_at,sequence"))
public class OrderAuditEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long sequence;
    @Column(name="event_id",nullable=false,unique=true,updatable=false) private UUID eventId;
    @Column(name="order_id",nullable=false,updatable=false) private Long orderId;
    @Column(nullable=false,updatable=false,columnDefinition="text") private String payload;
    @Column(name="occurred_at",nullable=false,updatable=false) private Instant occurredAt;
    @Column(name="published_at") private Instant publishedAt;
    protected OrderAuditEvent() { }
    public OrderAuditEvent(UUID id,Long orderId,String payload,Instant at) {
        this.eventId=id;this.orderId=orderId;this.payload=payload;this.occurredAt=at;
    }
}

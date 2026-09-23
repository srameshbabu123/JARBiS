package com.jarbis.brokerage.entity;

import com.jarbis.brokerage.enums.TransactionStatus;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(nullable = false)
    private Double quantity;

    @Column(nullable = false)
    private Double executionPrice;

    @OneToMany(
            mappedBy = "transaction",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<Order> participants = new ArrayList<>();

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    // Constructors
    public Transaction() {}

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public Double getQuantity() {
        return quantity;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

    public Double getExecutionPrice() {
        return executionPrice;
    }

    public void setExecutionPrice(Double executionPrice) {
        this.executionPrice = executionPrice;
    }

    public List<Order> getParticipants() {
        return participants;
    }

    public void setParticipants(List<Order> participants) {
        this.participants.clear();
        if (participants != null) {
            participants.forEach(this::addParticipant);
        }
    }

    public void addParticipant(Order order) {
        participants.add(order);
        order.setTransaction(this);
    }

    public void removeParticipant(Order order) {
        participants.remove(order);
        order.setTransaction(null);
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }
}

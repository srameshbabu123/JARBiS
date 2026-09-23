package com.jarbis.brokerage.entity;

import com.jarbis.brokerage.enums.OrderStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "orders")
public class Order {
    // id, asset, owner, status, side, quantity, price

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private OrderStatus status;

    // Constructors

    public Order() {}

    public Order(Asset asset, User owner, OrderStatus status) {
        this.asset = asset;
        this.owner = owner;
        this.status = status;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public Asset getAsset() {
        return asset;
    }

    public User getOwner() {
        return owner;
    }

    public OrderStatus getStatus() {
        return status;
    }
}

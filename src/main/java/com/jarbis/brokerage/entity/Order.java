package com.jarbis.brokerage.entity;

import com.jarbis.brokerage.enums.OrderSide;
import com.jarbis.brokerage.enums.OrderStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "orders")
public class Order {
    // id, asset, account, status, side, quantity, price

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderSide side;

    @Column(nullable = false)
    private Double quantity;

    @Column(nullable = false)
    private Double price;

    @ManyToOne
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    // Constructors

    public Order() {}

    public Order(Asset asset, Account account, OrderStatus status) {
        this.asset = asset;
        this.account = account;
        this.status = status;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public Asset getAsset() {
        return asset;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public Double getQuantity() {
        return quantity;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }
}

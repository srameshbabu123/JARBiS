package com.jarbis.brokerage.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "holdings")
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(nullable = false)
    private Double quantity;

    @Column(nullable = false)
    private Double averagePrice;

    // Constructors

    public Holding() {}

    public Holding(Account account, Asset asset, Double quantity, Double averagePrice) {
        this.account = account;
        this.asset = asset;
        this.quantity = quantity;
        this.averagePrice = averagePrice;
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

    public Double getQuantity() {
        return quantity;
    }

    public Double getAveragePrice() {
        return averagePrice;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

    public void setAveragePrice(Double averagePrice) {
        this.averagePrice = averagePrice;
    }

    public void setAccount(Account account) {
        this.account = account;
    }
}

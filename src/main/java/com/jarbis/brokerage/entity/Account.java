package com.jarbis.brokerage.entity;

import com.jarbis.brokerage.enums.AccountType;
import com.jarbis.brokerage.enums.Currency;
import jakarta.persistence.*;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Currency currency;

    @Column(nullable = false)
    private Double balance;

    @OneToMany(
            mappedBy = "account",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<Holding> holdings = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @OneToMany(
            mappedBy = "account",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<Order> orders = new ArrayList<>();

    // Constructors

    public Account() {}

    public Account(AccountType accountType,
                   Currency currency,
                   Double balance,
                   User owner) {
        this.accountType = accountType;
        this.currency = currency;
        this.balance = balance;
        this.owner = owner;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public Double getBalance() {
        return balance;
    }

    public void setBalance(Double balance) {
        this.balance = balance;
    }

    public List<Holding> getHoldings() {
        return holdings;
    }

    public void addHolding(Holding holding) {
        holdings.add(holding);
        holding.setAccount(this);
    }

    public void removeHolding(Holding holding) {
        holdings.remove(holding);
        holding.setAccount(null);
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public List<Order> getOrders() {
        return orders;
    }

    public void addOrder(Order order) {
        orders.add(order);
        order.setAccount(this);
    }

    public void removeOrder(Order order) {
        orders.remove(order);
        order.setAccount(null);
    }

}

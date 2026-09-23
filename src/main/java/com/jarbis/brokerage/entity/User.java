package com.jarbis.brokerage.entity;

import jakarta.persistence.*;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @OneToMany(
            mappedBy = "owner",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<Account> accounts = new ArrayList<>();

    // Constructors

    public User() {}

    public User(String fullName,
                String email,
                String passwordHash) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    // Getters and Setters

    public List<Account> getAccounts() {
        return accounts;
    }

    public Optional<Account> getAccount(Long accountId) {
        return accounts.stream()
                .filter(account ->
                        account.getId().equals(accountId))
                .findFirst();
    }

    public void addAccount(Account account) {
        accounts.add(account);
        account.setOwner(this);
    }

    public void removeAccount(Account account) {
        accounts.remove(account);
        account.setOwner(null);
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts;
    }
}

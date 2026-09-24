package com.jarbis.brokerage.dto.response;

import com.jarbis.brokerage.entity.Account;
import com.jarbis.brokerage.enums.AccountType;
import com.jarbis.brokerage.enums.Currency;

public class AccountResponseDto {

    private Long id;
    private AccountType accountType;
    private Currency currency;
    private Double balance;
    private Long ownerId;

    public AccountResponseDto() {
    }

    public AccountResponseDto(Long id, AccountType accountType, Currency currency, Double balance, Long ownerId) {
        this.id = id;
        this.accountType = accountType;
        this.currency = currency;
        this.balance = balance;
        this.ownerId = ownerId;
    }

    public static AccountResponseDto fromAccount(Account account) {
        Long ownerId = account.getOwner() != null ? account.getOwner().getId() : null;
        return new AccountResponseDto(
                account.getId(),
                account.getAccountType(),
                account.getCurrency(),
                account.getBalance(),
                ownerId);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }
}


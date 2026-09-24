package com.jarbis.brokerage.dto.request;

import com.jarbis.brokerage.enums.AccountType;
import com.jarbis.brokerage.enums.Currency;

public class CreateAccountRequestDto {

    private Long userId;
    private AccountType accountType;
    private Currency currency;
    private Double initialBalance;

    public CreateAccountRequestDto() {
    }


    public CreateAccountRequestDto(Long userId, AccountType accountType, Currency currency, Double initialBalance) {
        this.userId = userId;
        this.accountType = accountType;
        this.currency = currency;
        this.initialBalance = initialBalance;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public Double getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(Double initialBalance) {
        this.initialBalance = initialBalance;
    }
}


package com.jarbis.brokerage.dto.request;

import com.jarbis.brokerage.enums.AccountType;
import com.jarbis.brokerage.enums.Currency;

public class UpdateAccountRequestDto {

    private AccountType accountType;
    private Currency currency;

    public UpdateAccountRequestDto() {
    }

    public UpdateAccountRequestDto(AccountType accountType, Currency currency) {
        this.accountType = accountType;
        this.currency = currency;
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
}


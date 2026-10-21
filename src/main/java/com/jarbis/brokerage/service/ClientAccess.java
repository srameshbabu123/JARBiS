package com.jarbis.brokerage.service;

import com.jarbis.brokerage.repository.AccountRepository;
import org.springframework.stereotype.Component;

@Component("clientAccess")
public class ClientAccess {
    private final AccountRepository accounts;
    public ClientAccess(AccountRepository accounts) { this.accounts = accounts; }

    public boolean ownsAccount(Object principal, Long accountId) {
        return principal instanceof Long userId && accountId != null && accounts.existsByIdAndOwnerId(accountId, userId);
    }
}

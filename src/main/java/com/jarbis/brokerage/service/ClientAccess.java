package com.jarbis.brokerage.service;

import com.jarbis.brokerage.repository.AccountRepository;
import com.jarbis.brokerage.repository.OrderRepository;
import org.springframework.stereotype.Component;

@Component("clientAccess")
public class ClientAccess {
    private final AccountRepository accounts;
    private final OrderRepository orders;

    public ClientAccess(AccountRepository accounts, OrderRepository orders) {
        this.accounts = accounts;
        this.orders = orders;
    }

    public boolean ownsAccount(Object principal, Long accountId) {
        return principal instanceof Long userId && accountId != null && accounts.existsByIdAndOwnerId(accountId, userId);
    }

    public boolean ownsOrder(Object principal, Long orderId) {
        if (!(principal instanceof Long userId) || orderId == null) {
            return false;
        }
        return orders.findById(orderId)
                .map(order -> order.getAccount() != null
                        && order.getAccount().getOwner() != null
                        && userId.equals(order.getAccount().getOwner().getId()))
                .orElse(false);
    }
}

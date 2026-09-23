package com.jarbis.brokerage.repository;

import com.jarbis.brokerage.entity.Account;
import com.jarbis.brokerage.enums.AccountType;
import com.jarbis.brokerage.enums.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByOwnerId(Long userId);
    List<Account> findByAccountType(AccountType accountType);
    List<Account> findByCurrency(Currency currency);
    List<Account> findByOwnerIdAndAccountType(Long userId, AccountType accountType);
    List<Account> findByOwnerIdAndCurrency(Long userId, Currency currency);
}

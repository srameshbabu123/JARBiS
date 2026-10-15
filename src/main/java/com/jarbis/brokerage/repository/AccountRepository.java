package com.jarbis.brokerage.repository;

import com.jarbis.brokerage.entity.Account;
import com.jarbis.brokerage.enums.AccountType;
import com.jarbis.brokerage.enums.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
	// Account mutations (including order settlement) share the same row lock.
	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Account> findById(Long accountId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT a FROM Account a WHERE a.id = :accountId AND a.owner.id = :userId")
	Optional<Account> findOwnedForCashUpdate(@Param("userId") Long userId, @Param("accountId") Long accountId);

	List<Account> findByOwnerId(Long userId);
	List<Account> findByAccountType(AccountType accountType);
	List<Account> findByCurrency(Currency currency);
	List<Account> findByOwnerIdAndAccountType(Long userId, AccountType accountType);
	List<Account> findByOwnerIdAndCurrency(Long userId, Currency currency);
}

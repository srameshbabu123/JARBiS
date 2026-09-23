package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.*;
import com.jarbis.brokerage.enums.AccountType;
import com.jarbis.brokerage.enums.Currency;
import com.jarbis.brokerage.exception.AccountNotFoundException;
import com.jarbis.brokerage.exception.InsufficientBalanceException;
import com.jarbis.brokerage.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for Account management.
 * Handles account operations, balance management, and account queries.
 */
@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserService userService;

    @Autowired
    public AccountService(AccountRepository accountRepository, UserService userService) {
        this.accountRepository = accountRepository;
        this.userService = userService;
    }

    // ==================== CRUD Operations ====================

    /**
     * Create a new trading account for a user.
     */
    public Account createAccount(Long userId, AccountType accountType, Currency currency, Double initialBalance) {
        userService.verifyUserExists(userId);
        User user = userService.getUserOrThrow(userId);

        Account account = new Account(accountType, currency, initialBalance, user);
        return accountRepository.save(account);
    }

    /**
     * Get account by ID.
     */
    public Account getAccountById(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with id: " + accountId));
    }

    /**
     * Get all accounts for a specific user.
     */
    public List<Account> getAccountsByUser(Long userId) {
        userService.verifyUserExists(userId);
        return accountRepository.findByOwnerId(userId);
    }

    /**
     * Delete an account.
     */
    public void deleteAccount(Long accountId) {
        Account account = getAccountById(accountId);
        accountRepository.delete(account);
    }

    /**
     * Get all accounts (admin only).
     */
    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    // ==================== Query Operations ====================

    /**
     * Get all accounts of a specific type.
     */
    public List<Account> getAccountsByType(AccountType accountType) {
        return accountRepository.findByAccountType(accountType);
    }

    /**
     * Get all accounts with a specific currency.
     */
    public List<Account> getAccountsByCurrency(Currency currency) {
        return accountRepository.findByCurrency(currency);
    }

    /**
     * Get accounts for a user filtered by account type.
     */
    public List<Account> getAccountsByUserAndType(Long userId, AccountType accountType) {
        userService.verifyUserExists(userId);
        return accountRepository.findByOwnerIdAndAccountType(userId, accountType);
    }

    /**
     * Get accounts for a user filtered by currency.
     */
    public List<Account> getAccountsByUserAndCurrency(Long userId, Currency currency) {
        userService.verifyUserExists(userId);
        return accountRepository.findByOwnerIdAndCurrency(userId, currency);
    }

    // ==================== Balance Operations ====================

    /**
     * Get the current balance of an account.
     */
    public Double getAccountBalance(Long accountId) {
        Account account = getAccountById(accountId);
        return account.getBalance();
    }

    /**
     * Deposit funds into an account.
     */
    public Account deposit(Long accountId, Double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit amount must be greater than 0");
        }

        Account account = getAccountById(accountId);
        account.setBalance(account.getBalance() + amount);
        return accountRepository.save(account);
    }

    /**
     * Withdraw funds from an account.
     */
    public Account withdraw(Long accountId, Double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be greater than 0");
        }

        Account account = getAccountById(accountId);

        if (account.getBalance() < amount) {
            throw new InsufficientBalanceException(
                    "Insufficient balance. Available: " + account.getBalance() + ", Requested: " + amount);
        }

        account.setBalance(account.getBalance() - amount);
        return accountRepository.save(account);
    }

    /**
     * Transfer funds between two accounts.
     */
    public void transferFunds(Long fromAccountId, Long toAccountId, Double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than 0");
        }

        Account fromAccount = getAccountById(fromAccountId);
        Account toAccount = getAccountById(toAccountId);

        if (!fromAccount.getCurrency().equals(toAccount.getCurrency())) {
            throw new IllegalArgumentException(
                    "Cannot transfer between different currencies. From: " + fromAccount.getCurrency() 
                    + ", To: " + toAccount.getCurrency());
        }

        if (fromAccount.getBalance() < amount) {
            throw new InsufficientBalanceException(
                    "Insufficient balance in source account. Available: " + fromAccount.getBalance() 
                    + ", Requested: " + amount);
        }

        fromAccount.setBalance(fromAccount.getBalance() - amount);
        toAccount.setBalance(toAccount.getBalance() + amount);

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
    }

    // ==================== Holdings & Orders ====================

    /**
     * Get all holdings in an account.
     */
    public List<Holding> getAccountHoldings(Long accountId) {
        Account account = getAccountById(accountId);
        return account.getHoldings();
    }

    /**
     * Get all orders in an account.
     */
    public List<Order> getAccountOrders(Long accountId) {
        Account account = getAccountById(accountId);
        return account.getOrders();
    }

    // ==================== Account Management ====================

    /**
     * Close an account (soft delete - can be enhanced with a status field).
     */
    public void closeAccount(Long accountId) {
        Account account = getAccountById(accountId);

        if (account.getBalance() != 0) {
            throw new IllegalArgumentException(
                    "Cannot close account with non-zero balance. Current balance: " + account.getBalance());
        }

        if (!account.getHoldings().isEmpty()) {
            throw new IllegalArgumentException("Cannot close account with active holdings");
        }

        deleteAccount(accountId);
    }

    /**
     * Get the total value of all holdings in an account (requires asset price).
     */
    public Double getAccountPortfolioValue(Long accountId) {
        Account account = getAccountById(accountId);
        return account.getBalance() + account.getHoldings().stream()
                .mapToDouble(holding -> holding.getQuantity() * holding.getAsset().getPrice())
                .sum();
    }

    // ==================== Asset Trading ====================

    /**
     * Buy an asset with funds from the account.
     * Validates sufficient balance and updates holdings.
     */
    public Holding buyAsset(Long accountId, Asset asset, Double quantity, Double pricePerUnit) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        if (pricePerUnit <= 0) {
            throw new IllegalArgumentException("Price per unit must be greater than 0");
        }

        Account account = getAccountById(accountId);
        Double totalCost = quantity * pricePerUnit;

        if (account.getBalance() < totalCost) {
            throw new InsufficientBalanceException(
                    "Insufficient balance to buy asset. Available: " + account.getBalance() 
                    + ", Required: " + totalCost);
        }

        // Deduct balance
        account.setBalance(account.getBalance() - totalCost);

        // Find or create holding
        Holding holding = account.getHoldings().stream()
                .filter(h -> h.getAsset().getId().equals(asset.getId()))
                .findFirst()
                .orElse(null);

        if (holding != null) {
            // Update existing holding with weighted average price
            Double totalQuantity = holding.getQuantity() + quantity;
            Double newAveragePrice = ((holding.getQuantity() * holding.getAveragePrice()) 
                    + (quantity * pricePerUnit)) / totalQuantity;
            holding.setQuantity(totalQuantity);
            holding.setAveragePrice(newAveragePrice);
        } else {
            // Create new holding
            holding = new Holding(asset, quantity, pricePerUnit);
            account.getHoldings().add(holding);
        }

        accountRepository.save(account);
        return holding;
    }

    /**
     * Sell an asset from the account.
     * Validates sufficient holdings and credits balance.
     */
    public Double sellAsset(Long accountId, Asset asset, Double quantity, Double pricePerUnit) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        if (pricePerUnit <= 0) {
            throw new IllegalArgumentException("Price per unit must be greater than 0");
        }

        Account account = getAccountById(accountId);

        // Find holding
        Holding holding = account.getHoldings().stream()
                .filter(h -> h.getAsset().getId().equals(asset.getId()))
                .findFirst()
                .orElse(null);

        if (holding == null || holding.getQuantity() < quantity) {
            Double availableQuantity = (holding != null) ? holding.getQuantity() : 0.0;
            throw new IllegalArgumentException(
                    "Insufficient holdings to sell. Available: " + availableQuantity 
                    + ", Requested: " + quantity);
        }

        // Calculate proceeds
        Double proceeds = quantity * pricePerUnit;

        // Update holding quantity
        Double remainingQuantity = holding.getQuantity() - quantity;
        if (remainingQuantity <= 0) {
            account.getHoldings().remove(holding);
        } else {
            holding.setQuantity(remainingQuantity);
        }

        // Credit balance
        account.setBalance(account.getBalance() + proceeds);

        accountRepository.save(account);
        return proceeds;
    }
}

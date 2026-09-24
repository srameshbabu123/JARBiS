package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.Account;
import com.jarbis.brokerage.entity.Asset;
import com.jarbis.brokerage.entity.Holding;
import com.jarbis.brokerage.entity.Order;
import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.enums.AccountType;
import com.jarbis.brokerage.enums.Currency;
import com.jarbis.brokerage.exception.AccountClosureException;
import com.jarbis.brokerage.exception.AccountCurrencyMismatchException;
import com.jarbis.brokerage.exception.AccountNotFoundException;
import com.jarbis.brokerage.exception.InvalidAmountException;
import com.jarbis.brokerage.exception.InsufficientBalanceException;
import com.jarbis.brokerage.exception.InsufficientHoldingException;
import com.jarbis.brokerage.exception.UserNotFoundException;
import com.jarbis.brokerage.repository.AccountRepository;
import com.jarbis.brokerage.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final HoldingService holdingService;

    @Autowired
    public AccountService(AccountRepository accountRepository, UserRepository userRepository,
                          HoldingService holdingService) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.holdingService = holdingService;
    }

    // ==================== CRUD Operations ====================

    /**
     * Create a new trading account for a user.
     */
    public Account createAccount(Long userId, AccountType accountType, Currency currency) {
        return createAccount(userId, accountType, currency, 0.0);
    }

    /**
     * Create a new trading account for a user with an explicit initial balance.
     */
    public Account createAccount(Long userId, AccountType accountType, Currency currency, Double initialBalance) {
        User user = getUserOrThrow(userId);
        Double startingBalance = (initialBalance != null) ? initialBalance : 0.0;

        Account account = new Account(accountType, currency, startingBalance, user);
        user.addAccount(account);
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
     * Update editable account details.
     */
    public Account updateAccount(Long accountId, AccountType accountType, Currency currency) {
        Account account = getAccountById(accountId);

        if (accountType != null) {
            account.setAccountType(accountType);
        }

        if (currency != null) {
            account.setCurrency(currency);
        }

        return accountRepository.save(account);
    }

    /**
     * Get all accounts for a specific user.
     */
    public List<Account> getAccountsByUser(Long userId) {
        verifyUserExists(userId);
        return accountRepository.findByOwnerId(userId);
    }

    /**
     * Get a specific account for a specific user.
     */
    public Account getAccountByUser(Long userId, Long accountId) {
        verifyUserExists(userId);
        Account account = getAccountById(accountId);

        if (!account.getOwner().getId().equals(userId)) {
            throw new AccountNotFoundException("Account not found with id: " + accountId);
        }

        return account;
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
        verifyUserExists(userId);
        return accountRepository.findByOwnerIdAndAccountType(userId, accountType);
    }

    /**
     * Get accounts for a user filtered by currency.
     */
    public List<Account> getAccountsByUserAndCurrency(Long userId, Currency currency) {
        verifyUserExists(userId);
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
            throw new InvalidAmountException("Deposit amount must be greater than 0");
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
            throw new InvalidAmountException("Withdrawal amount must be greater than 0");
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
            throw new InvalidAmountException("Transfer amount must be greater than 0");
        }

        Account fromAccount = getAccountById(fromAccountId);
        Account toAccount = getAccountById(toAccountId);

        if (!fromAccount.getCurrency().equals(toAccount.getCurrency())) {
            throw new AccountCurrencyMismatchException(
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
            throw new AccountClosureException(
                    "Cannot close account with non-zero balance. Current balance: " + account.getBalance());
        }

        if (!account.getHoldings().isEmpty()) {
            throw new AccountClosureException("Cannot close account with active holdings");
        }

        deleteAccount(accountId);
    }

    /**
     * Close an account after validating that it belongs to the given user.
     */
    public void closeAccount(Long userId, Long accountId) {
        getAccountByUser(userId, accountId);
        closeAccount(accountId);
    }

    /**
     * Get the total portfolio value of an account using current market prices.
     */
    public Double getAccountPortfolioValue(Long accountId) {
        Account account = getAccountById(accountId);
        return account.getBalance() + account.getHoldings().stream()
                .mapToDouble(holdingService::getMarketValue)
                .sum();
    }

    // ==================== Asset Trading ====================

    /**
     * Buy an asset with funds from the account.
     * Validates sufficient balance and updates holdings.
     */
    public void buyAsset(Long accountId, Asset asset, Double quantity, Double pricePerUnit) {
        if (quantity <= 0) {
            throw new InvalidAmountException("Quantity must be greater than 0");
        }

        if (pricePerUnit <= 0) {
            throw new InvalidAmountException("Price per unit must be greater than 0");
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
            holding = new Holding(account, asset, quantity, pricePerUnit);
            account.addHolding(holding);
        }

        accountRepository.save(account);
    }

    /**
     * Sell an asset from the account.
     * Validates sufficient holdings and credits balance.
     */
    public void sellAsset(Long accountId, Asset asset, Double quantity, Double pricePerUnit) {
        if (quantity <= 0) {
            throw new InvalidAmountException("Quantity must be greater than 0");
        }

        if (pricePerUnit <= 0) {
            throw new InvalidAmountException("Price per unit must be greater than 0");
        }

        Account account = getAccountById(accountId);

        // Find holding
        Holding holding = account.getHoldings().stream()
                .filter(h -> h.getAsset().getId().equals(asset.getId()))
                .findFirst()
                .orElse(null);

        if (holding == null || holding.getQuantity() < quantity) {
            Double availableQuantity = (holding != null) ? holding.getQuantity() : 0.0;
            throw new InsufficientHoldingException(
                    "Insufficient holdings to sell. Available: " + availableQuantity 
                    + ", Requested: " + quantity);
        }

        // Calculate proceeds
        Double proceeds = quantity * pricePerUnit;

        // Update holding quantity
        Double remainingQuantity = holding.getQuantity() - quantity;
        if (remainingQuantity <= 0) {
            account.removeHolding(holding);
        } else {
            holding.setQuantity(remainingQuantity);
        }

        // Credit balance
        account.setBalance(account.getBalance() + proceeds);

        accountRepository.save(account);
    }

    private void verifyUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("User not found with id: " + userId);
        }
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
    }
}

package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.Account;
import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.enums.AccountType;
import com.jarbis.brokerage.enums.Currency;
import com.jarbis.brokerage.exception.EmailAlreadyExistsException;
import com.jarbis.brokerage.exception.UserNotFoundException;
import com.jarbis.brokerage.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for User management.
 * Handles user creation, authentication, account management, and profile updates.
 */
@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ==================== Helper Methods ====================

    /**
     * Verify that a user exists by ID. Throws exception if not found.
     * This is used internally by other services to validate user ownership.
     */
    public void verifyUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("User not found with id: " + userId);
        }
    }

    /**
     * Get user by ID or throw exception.
     * Internal helper to avoid repeating the same pattern.
     */
    public User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
    }

    // ==================== CRUD Operations ====================

    /**
     * Create a new user with hashed password.
     */
    public User createUser(String fullName, String email, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));

        return userRepository.save(user);
    }

    /**
     * Get user by ID.
     */
    public User getUserById(Long id) {
        return getUserOrThrow(id);
    }

    /**
     * Get user by email address.
     */
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
    }

    /**
     * Update user profile information.
     */
    public User updateUser(Long id, String fullName, String email) {
        User user = getUserById(id);

        if (email != null && !email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        if (fullName != null) {
            user.setFullName(fullName);
        }
        if (email != null) {
            user.setEmail(email);
        }

        return userRepository.save(user);
    }

    /**
     * Delete a user and all associated accounts.
     */
    public void deleteUser(Long id) {
        User user = getUserById(id);
        userRepository.delete(user);
    }

    /**
     * Check if user exists by email.
     */
    public boolean userExists(String email) {
        return userRepository.existsByEmail(email);
    }

    // ==================== Account Management ====================

    /**
     * Create a new trading account for the user.
     */
    public Account createAccount(Long userId, AccountType accountType, Currency currency) {
        User user = getUserById(userId);

        Account account = new Account(accountType, currency, 0.0, user);
        user.addAccount(account);

        userRepository.save(user);
        return account;
    }

    /**
     * Get all accounts for a user.
     */
    public List<Account> getUserAccounts(Long userId) {
        User user = getUserById(userId);
        return user.getAccounts();
    }

    /**
     * Get a specific account by user ID and account ID.
     */
    public Account getAccountById(Long userId, Long accountId) {
        User user = getUserById(userId);

        return user.getAccount(accountId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Account not found with id: " + accountId));
    }

    /**
     * Close/delete an account for the user.
     */
    public void closeAccount(Long userId, Long accountId) {
        User user = getUserById(userId);
        Account account = getAccountById(userId, accountId);

        user.removeAccount(account);
        userRepository.save(user);
    }

    // ==================== Security ====================

    /**
     * Verify user credentials for authentication.
     */
    public boolean verifyPassword(String email, String password) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return false;
        }
        return passwordEncoder.matches(password, user.getPasswordHash());
    }

    /**
     * Change user password.
     */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = getUserById(userId);

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Old password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * Hash a password using the configured password encoder.
     */
    public String hashPassword(String password) {
        return passwordEncoder.encode(password);
    }

    // ==================== Analytics ====================

    /**
     * Calculate total portfolio balance across all user accounts.
     */
    public Double getUserBalance(Long userId) {
        User user = getUserById(userId);
        return user.getAccounts().stream()
                .mapToDouble(Account::getBalance)
                .sum();
    }
}


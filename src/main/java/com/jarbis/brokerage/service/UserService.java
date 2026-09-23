package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.Account;
import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.exception.EmailAlreadyExistsException;
import com.jarbis.brokerage.exception.UserNotFoundException;
import com.jarbis.brokerage.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/**
 * Service layer for User management.
 * Handles user creation, authentication, and profile updates.
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
            throw new com.jarbis.brokerage.exception.InvalidCredentialsException("Old password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
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

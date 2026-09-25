package com.jarbis.brokerage.controller;

import com.jarbis.brokerage.dto.request.CreateUserRequestDto;
import com.jarbis.brokerage.dto.response.UserResponseDto;
import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for User management operations.
 */
@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Create a new user.
     *
     * @param requestDto the user creation request
     * @return the created user response
     */
    @PostMapping
    public ResponseEntity<UserResponseDto> createUser(@RequestBody CreateUserRequestDto requestDto) {
        User user = userService.createUser(
                requestDto.getFullName(),
                requestDto.getEmail(),
                requestDto.getPassword()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponseDto.fromUser(user));
    }

    /**
     * Retrieve a user by ID.
     *
     * @param userId the user ID
     * @return the user response
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable Long userId) {
        User user = userService.getUserById(userId);
        return ResponseEntity.ok(UserResponseDto.fromUser(user));
    }

    /**
     * Update user profile information.
     *
     * @param userId the user ID
     * @param requestDto the update request
     * @return the updated user response
     */
    @PutMapping("/{userId}")
    public ResponseEntity<UserResponseDto> updateUser(
            @PathVariable Long userId,
            @RequestBody CreateUserRequestDto requestDto) {
        User user = userService.updateUser(
                userId,
                requestDto.getFullName(),
                requestDto.getEmail()
        );
        return ResponseEntity.ok(UserResponseDto.fromUser(user));
    }

    /**
     * Delete a user by ID.
     *
     * @param userId the user ID
     * @return no content response
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get user balance across all accounts.
     *
     * @param userId the user ID
     * @return the total balance
     */
    @GetMapping("/{userId}/balance")
    public ResponseEntity<Double> getUserBalance(@PathVariable Long userId) {
        Double balance = userService.getUserBalance(userId);
        return ResponseEntity.ok(balance);
    }
}

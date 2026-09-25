package com.jarbis.brokerage.dto.response;

import com.jarbis.brokerage.entity.User;

/**
 * DTO for user responses. Excludes sensitive information like password hash.
 */
public class UserResponseDto {
    private Long id;
    private String fullName;
    private String email;

    // Constructors
    public UserResponseDto() {}

    public UserResponseDto(Long id, String fullName, String email) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
    }

    // Factory method to create from User entity
    public static UserResponseDto fromUser(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getFullName(),
                user.getEmail()
        );
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}

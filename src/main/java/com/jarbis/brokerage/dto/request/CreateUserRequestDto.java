package com.jarbis.brokerage.dto.request;

/**
 * DTO for creating a new user.
 */
public class CreateUserRequestDto {
    private String fullName;
    private String email;
    private String password;

    // Constructors
    public CreateUserRequestDto() {}

    public CreateUserRequestDto(String fullName, String email, String password) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
    }

    // Getters and Setters
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

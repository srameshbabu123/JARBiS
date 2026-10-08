package com.jarbis.brokerage.dto.response;

public class AuthResponseDto {
	private UserResponseDto user;
	private String token;
	private String tokenType;

	public AuthResponseDto() {
	}

	public AuthResponseDto(UserResponseDto user, String token) {
		this.user = user;
		this.token = token;
		this.tokenType = "Bearer";
	}

	public UserResponseDto getUser() {
		return user;
	}

	public void setUser(UserResponseDto user) {
		this.user = user;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public String getTokenType() {
		return tokenType;
	}

	public void setTokenType(String tokenType) {
		this.tokenType = tokenType;
	}
}
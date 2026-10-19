package com.jarbis.brokerage.controller;

import com.jarbis.brokerage.dto.request.CreateUserRequestDto;
import com.jarbis.brokerage.dto.request.LoginRequestDto;
import com.jarbis.brokerage.dto.response.AuthResponseDto;
import com.jarbis.brokerage.dto.response.UserResponseDto;
import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.service.JwtService;
import com.jarbis.brokerage.service.RefreshTokenService;
import com.jarbis.brokerage.service.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
	private final UserService userService;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;
	private final boolean secureRefreshCookie;

	public AuthController(UserService userService, JwtService jwtService) {
		this(userService, jwtService, null, true);
	}

	@Autowired
	public AuthController(UserService userService, JwtService jwtService, RefreshTokenService refreshTokenService,
			@Value("${jwt.refresh-cookie-secure:true}") boolean secureRefreshCookie) {
		this.userService = userService;
		this.jwtService = jwtService;
		this.refreshTokenService = refreshTokenService;
		this.secureRefreshCookie = secureRefreshCookie;
	}

	@PostMapping("/register")
	public ResponseEntity<AuthResponseDto> register(@RequestBody CreateUserRequestDto requestDto) {
		User user = userService.createUser(requestDto.getFullName(), requestDto.getEmail(), requestDto.getPassword());
		String token = jwtService.generateToken(user);
		ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED);
		addRefreshCookie(response, user);
		return response.body(new AuthResponseDto(UserResponseDto.fromUser(user), token));
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponseDto> login(@RequestBody LoginRequestDto requestDto) {
		if (!userService.verifyPassword(requestDto.getEmail(), requestDto.getPassword())) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		User user = userService.getUserByEmail(requestDto.getEmail());
		String token = jwtService.generateToken(user);
		ResponseEntity.BodyBuilder response = ResponseEntity.ok();
		addRefreshCookie(response, user);
		return response.body(new AuthResponseDto(UserResponseDto.fromUser(user), token));
	}

	@PostMapping("/refresh")
	public ResponseEntity<AuthResponseDto> refresh(
			@CookieValue(name = "refresh_token", required = false) String rawRefreshToken) {
		if (refreshTokenService == null) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}

		try {
			RefreshTokenService.IssuedRefreshToken issued = refreshTokenService.rotate(rawRefreshToken);
			User user = issued.token().getUser();
			ResponseEntity.BodyBuilder response = ResponseEntity.ok();
			addRefreshCookie(response, issued.rawValue());
			return response.body(new AuthResponseDto(UserResponseDto.fromUser(user), jwtService.generateToken(user)));
		} catch (RefreshTokenService.InvalidRefreshTokenException exception) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			@CookieValue(name = "refresh_token", required = false) String rawRefreshToken) {
		if (refreshTokenService != null) {
			refreshTokenService.revoke(rawRefreshToken);
		}
		return ResponseEntity.noContent().header("Set-Cookie", clearRefreshCookie().toString()).build();
	}

	@GetMapping("/me")
	public ResponseEntity<UserResponseDto> me() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || authentication.getPrincipal() == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		Long userId = (Long) authentication.getPrincipal();
		User user = userService.getUserById(userId);
		return ResponseEntity.ok(UserResponseDto.fromUser(user));
	}

	private void addRefreshCookie(ResponseEntity.BodyBuilder response, User user) {
		if (refreshTokenService != null) {
			addRefreshCookie(response, refreshTokenService.issue(user).rawValue());
		}
	}

	private void addRefreshCookie(ResponseEntity.BodyBuilder response, String rawRefreshToken) {
		response.header("Set-Cookie", refreshCookie(rawRefreshToken).toString());
	}

	private ResponseCookie refreshCookie(String value) {
		return ResponseCookie.from("refresh_token", value)
				.httpOnly(true)
				.secure(secureRefreshCookie)
				.sameSite("Strict")
				.path("/auth")
				.maxAge(2592000)
				.build();
	}

	private ResponseCookie clearRefreshCookie() {
		return ResponseCookie.from("refresh_token", "")
				.httpOnly(true)
				.secure(secureRefreshCookie)
				.sameSite("Strict")
				.path("/auth")
				.maxAge(0)
				.build();
	}
}
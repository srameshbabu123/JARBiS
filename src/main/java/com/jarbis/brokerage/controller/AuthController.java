package com.jarbis.brokerage.controller;

import com.jarbis.brokerage.dto.request.CreateUserRequestDto;
import com.jarbis.brokerage.dto.request.LoginRequestDto;
import com.jarbis.brokerage.dto.response.AuthResponseDto;
import com.jarbis.brokerage.dto.response.UserResponseDto;
import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.service.JwtService;
import com.jarbis.brokerage.service.UserService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
	private final UserService userService;
	private final JwtService jwtService;

	public AuthController(UserService userService, JwtService jwtService) {
		this.userService = userService;
		this.jwtService = jwtService;
	}

	@PostMapping("/register")
	public ResponseEntity<AuthResponseDto> register(@RequestBody CreateUserRequestDto requestDto) {
		User user = userService.createUser(requestDto.getFullName(), requestDto.getEmail(), requestDto.getPassword());
		String token = jwtService.generateToken(user);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new AuthResponseDto(UserResponseDto.fromUser(user), token));
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponseDto> login(@RequestBody LoginRequestDto requestDto) {
		if (!userService.verifyPassword(requestDto.getEmail(), requestDto.getPassword())) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		User user = userService.getUserByEmail(requestDto.getEmail());
		String token = jwtService.generateToken(user);
		return ResponseEntity.ok(new AuthResponseDto(UserResponseDto.fromUser(user), token));
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
}
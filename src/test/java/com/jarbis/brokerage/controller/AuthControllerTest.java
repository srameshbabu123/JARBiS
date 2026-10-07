package com.jarbis.brokerage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jarbis.brokerage.dto.request.CreateUserRequestDto;
import com.jarbis.brokerage.dto.request.LoginRequestDto;
import com.jarbis.brokerage.dto.response.AuthResponseDto;
import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.service.JwtService;
import com.jarbis.brokerage.service.UserService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AuthControllerTest {
	private UserService userService;
	private JwtService jwtService;
	private AuthController authController;

	@BeforeEach
	void setUp() {
		userService = mock(UserService.class);
		jwtService = mock(JwtService.class);
		authController = new AuthController(userService, jwtService);
	}

	@Nested
	class RegisterTests {
		@Test
		void testRegisterSuccess() {
			CreateUserRequestDto requestDto = new CreateUserRequestDto("John Doe", "john@example.com", "password123");
			User user = new User("John Doe", "john@example.com", "hashedPassword");
			user.setId(1L);

			when(userService.createUser("John Doe", "john@example.com", "password123")).thenReturn(user);
			when(jwtService.generateToken(user)).thenReturn("token-123");

			ResponseEntity<AuthResponseDto> response = authController.register(requestDto);

			assertEquals(HttpStatus.CREATED, response.getStatusCode());
			assertNotNull(response.getBody());
			assertEquals("token-123", response.getBody().getToken());
			assertEquals("John Doe", response.getBody().getUser().getFullName());
			verify(userService).createUser("John Doe", "john@example.com", "password123");
			verify(jwtService).generateToken(user);
		}
	}

	@Nested
	class LoginTests {
		@Test
		void testLoginSuccess() {
			LoginRequestDto requestDto = new LoginRequestDto("john@example.com", "password123");
			User user = new User("John Doe", "john@example.com", "hashedPassword");
			user.setId(1L);

			when(userService.verifyPassword("john@example.com", "password123")).thenReturn(true);
			when(userService.getUserByEmail("john@example.com")).thenReturn(user);
			when(jwtService.generateToken(user)).thenReturn("token-456");

			ResponseEntity<AuthResponseDto> response = authController.login(requestDto);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertNotNull(response.getBody());
			assertEquals("token-456", response.getBody().getToken());
			verify(userService).verifyPassword("john@example.com", "password123");
			verify(userService).getUserByEmail("john@example.com");
			verify(jwtService).generateToken(user);
		}

		@Test
		void testLoginFailureReturnsUnauthorized() {
			LoginRequestDto requestDto = new LoginRequestDto("john@example.com", "wrong-password");

			when(userService.verifyPassword("john@example.com", "wrong-password")).thenReturn(false);

			ResponseEntity<AuthResponseDto> response = authController.login(requestDto);

			assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		}
	}
}
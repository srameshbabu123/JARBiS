package com.jarbis.brokerage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.jarbis.brokerage.entity.RefreshToken;
import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.repository.RefreshTokenRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RefreshTokenServiceTest {
	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	private RefreshTokenRepository repository;
	private RefreshTokenService service;

	@BeforeEach
	void setUp() {
		repository = mock(RefreshTokenRepository.class);
		service = new RefreshTokenService(repository, Duration.ofDays(30),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void issueStoresOnlyAHashAndSetsExpiry() {
		User user = new User();
		when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
			RefreshToken token = invocation.getArgument(0);
			token.setReplacedBy(UUID.randomUUID());
			return token;
		});

		RefreshTokenService.IssuedRefreshToken issued = service.issue(user);

		assertNotNull(issued.rawValue());
		assertEquals(64, issued.token().getTokenHash().length());
		assertEquals(NOW.plus(Duration.ofDays(30)), issued.token().getExpiresAt());
		verify(repository).save(issued.token());
	}

	@Test
	void rotateRevokesCurrentTokenAndIssuesReplacement() {
		User user = new User();
		RefreshToken current = new RefreshToken();
		current.setUser(user);
		current.setTokenHash(hashForTest("old-token"));
		current.setExpiresAt(NOW.plus(Duration.ofDays(1)));
		when(repository.findByTokenHash(current.getTokenHash())).thenReturn(Optional.of(current));
		when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

		RefreshTokenService.IssuedRefreshToken replacement = service.rotate("old-token");

		assertNotNull(current.getRevokedAt());
		assertEquals(NOW, current.getLastUsedAt());
		assertNotNull(replacement.rawValue());
		verify(repository).save(current);
	}

	@Test
	void rotateRejectsRevokedToken() {
		RefreshToken revoked = new RefreshToken();
		revoked.setTokenHash(hashForTest("revoked-token"));
		revoked.setExpiresAt(NOW.plus(Duration.ofDays(1)));
		revoked.setRevokedAt(NOW.minusSeconds(1));
		when(repository.findByTokenHash(revoked.getTokenHash())).thenReturn(Optional.of(revoked));

		assertThrows(RefreshTokenService.InvalidRefreshTokenException.class,
				() -> service.rotate("revoked-token"));
	}

	private static String hashForTest(String value) {
		try {
			return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException exception) {
			throw new AssertionError(exception);
		}
	}
}
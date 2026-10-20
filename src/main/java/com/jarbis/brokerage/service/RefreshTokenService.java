package com.jarbis.brokerage.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import com.jarbis.brokerage.entity.RefreshToken;
import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.repository.RefreshTokenRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RefreshTokenService {
	private final RefreshTokenRepository refreshTokenRepository;
	private final Duration refreshTokenLifetime;
	private final Clock clock;
	private final SecureRandom secureRandom = new SecureRandom();

	@Autowired
	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
			@Value("${jwt.refresh-expiration-ms:2592000000}") long refreshTokenExpirationMs) {
		this(refreshTokenRepository, Duration.ofMillis(refreshTokenExpirationMs), Clock.systemUTC());
	}

	RefreshTokenService(RefreshTokenRepository refreshTokenRepository, Duration refreshTokenLifetime, Clock clock) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.refreshTokenLifetime = refreshTokenLifetime;
		this.clock = clock;
	}

	public IssuedRefreshToken issue(User user) {
		String rawToken = generateToken();
		Instant now = clock.instant();
		RefreshToken refreshToken = new RefreshToken();
		refreshToken.setUser(user);
		refreshToken.setTokenHash(hash(rawToken));
		refreshToken.setCreatedAt(now);
		refreshToken.setExpiresAt(now.plus(refreshTokenLifetime));
		refreshTokenRepository.save(refreshToken);
		return new IssuedRefreshToken(rawToken, refreshToken);
	}

	public IssuedRefreshToken rotate(String rawToken) {
		RefreshToken current = findUsable(rawToken)
				.orElseThrow(InvalidRefreshTokenException::new);
		IssuedRefreshToken replacement = issue(current.getUser());
		Instant now = clock.instant();
		current.setLastUsedAt(now);
		current.setRevokedAt(now);
		current.setReplacedBy(replacement.token().getId());
		refreshTokenRepository.save(current);
		return replacement;
	}

	public void revoke(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return;
		}
		refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
			if (token.getRevokedAt() == null) {
				token.setRevokedAt(clock.instant());
				refreshTokenRepository.save(token);
			}
		});
	}

	private Optional<RefreshToken> findUsable(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return Optional.empty();
		}
		Instant now = clock.instant();
		return refreshTokenRepository.findByTokenHash(hash(rawToken))
				.filter(token -> token.getRevokedAt() == null && token.getExpiresAt().isAfter(now));
	}

	private String generateToken() {
		byte[] token = new byte[48];
		secureRandom.nextBytes(token);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
	}

	private String hash(String rawToken) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}

	public record IssuedRefreshToken(String rawValue, RefreshToken token) {
	}

	public static class InvalidRefreshTokenException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
}

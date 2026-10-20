package com.jarbis.brokerage.repository;

import java.util.Optional;
import java.util.UUID;

import com.jarbis.brokerage.entity.RefreshToken;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
	Optional<RefreshToken> findByTokenHash(String tokenHash);
}
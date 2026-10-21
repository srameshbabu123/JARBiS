package com.jarbis.brokerage.service;

import com.jarbis.brokerage.marketdata.MarketDataProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MarketDataRetentionCleanupService Tests")
class MarketDataRetentionCleanupServiceTest {

	private MarketDataProperties properties;
	private RedisMarketDataService redisService;
	private MarketDataPostgresService postgresService;
	private MarketDataRetentionCleanupService cleanupService;

	@BeforeEach
	void setUp() {
		properties = new MarketDataProperties();
		redisService = mock(RedisMarketDataService.class);
		postgresService = mock(MarketDataPostgresService.class);
		cleanupService = new MarketDataRetentionCleanupService(properties, redisService, postgresService);
	}

	@Test
	@DisplayName("Should purge Redis and PostgreSQL snapshots before retention cutoff")
	void cleanupExpiredSnapshotsPurgesBothStores() {
		when(redisService.purgeSnapshotsBefore(any(Instant.class))).thenReturn(10L);
		when(postgresService.purgeSnapshotsBefore(any(Instant.class))).thenReturn(5L);

		cleanupService.cleanupExpiredSnapshots();

		verify(redisService).purgeSnapshotsBefore(any(Instant.class));
		verify(postgresService).purgeSnapshotsBefore(any(Instant.class));
	}

	@Test
	@DisplayName("Should reject missing retention window")
	void cleanupExpiredSnapshotsRejectsInvalidRetentionWindow() {
		properties.getRedis().setRetentionWindow(Duration.ZERO);

		assertThrows(IllegalStateException.class, cleanupService::cleanupExpiredSnapshots);
	}
}
package com.jarbis.brokerage.service;

import com.jarbis.brokerage.marketdata.MarketDataEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;

import static org.mockito.Mockito.inOrder;

@DisplayName("MarketDataPersistenceService Tests")
class MarketDataPersistenceServiceTest {

	private MarketDataPersistenceService persistenceService;

	@Mock
	private MarketDataPostgresService postgresService;

	@Mock
	private RedisMarketDataService redisService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		persistenceService = new MarketDataPersistenceService(postgresService, redisService);
	}

	@Test
	@DisplayName("Should write PostgreSQL before Redis")
	void persistWritesPostgresBeforeRedis() {
		MarketDataEvent event = event();

		persistenceService.persist(event);

		InOrder inOrder = inOrder(postgresService, redisService);
		inOrder.verify(postgresService).storeSnapshot(event);
		inOrder.verify(redisService).storeSnapshot(event);
	}

	private MarketDataEvent event() {
		Instant timestamp = Instant.parse("2026-10-13T19:30:00Z");
		return new MarketDataEvent("AAPL", 210.15, 210.1, 210.2, 4.0, "USD", 1.0, 0.5, 209.15, "cache",
				false, "open", timestamp, timestamp.plusSeconds(1));
	}
}

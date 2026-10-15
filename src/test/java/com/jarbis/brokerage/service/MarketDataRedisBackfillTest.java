package com.jarbis.brokerage.service;

import com.jarbis.brokerage.marketdata.MarketDataEvent;
import com.jarbis.brokerage.marketdata.MarketDataProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MarketDataRedisBackfill Tests")
class MarketDataRedisBackfillTest {

	private MarketDataRedisBackfill backfill;

	@Mock
	private MarketDataPostgresService postgresService;

	@Mock
	private RedisMarketDataService redisService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		backfill = new MarketDataRedisBackfill(postgresService, redisService, new MarketDataProperties());
	}

	@Test
	@DisplayName("Should restore PostgreSQL snapshots into Redis at startup")
	void runRestoresSnapshots() {
		MarketDataEvent event = event();
		when(postgresService.findSnapshots(any(Instant.class), any(Instant.class))).thenReturn(List.of(event));

		backfill.run(new DefaultApplicationArguments(new String[0]));

		verify(postgresService).findSnapshots(any(Instant.class), any(Instant.class));
		verify(redisService).storeSnapshot(event);
	}

	private MarketDataEvent event() {
		Instant timestamp = Instant.now().minusSeconds(3600);
		return new MarketDataEvent("AAPL", 210.15, 210.1, 210.2, 4.0, "USD", 1.0, 0.5, 209.15, "cache",
				false, "open", timestamp, timestamp.plusSeconds(1));
	}
}

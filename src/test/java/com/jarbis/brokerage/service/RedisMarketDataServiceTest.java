package com.jarbis.brokerage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarbis.brokerage.marketdata.MarketDataEvent;
import com.jarbis.brokerage.marketdata.MarketDataProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RedisMarketDataService Tests")
class RedisMarketDataServiceTest {

	private RedisMarketDataService redisMarketDataService;

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ZSetOperations<String, String> zSetOperations;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		MarketDataProperties properties = new MarketDataProperties();
		properties.getRedis().setKeyPrefix("market-data:");
		when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

		redisMarketDataService = new RedisMarketDataService(redisTemplate,
				new ObjectMapper().findAndRegisterModules(), properties);
	}

	@Test
	@DisplayName("Should store a quote in a symbol-specific sorted set")
	void storeSnapshotAddsTimestampedEvent() {
		MarketDataEvent event = event("aapl");

		redisMarketDataService.storeSnapshot(event);

		verify(zSetOperations).add(eq("market-data:AAPL"), anyString(),
				eq((double) event.asOf().toEpochMilli()));
		verify(zSetOperations).removeRangeByScore(eq("market-data:AAPL"), eq(Double.NEGATIVE_INFINITY),
				anyDouble());
	}

	@Test
	@DisplayName("Should remove entries older than the retention window")
	void storeSnapshotTrimsEntriesOutsideRetentionWindow() {
		MarketDataEvent event = event("MSFT");

		redisMarketDataService.storeSnapshot(event);

		verify(zSetOperations).removeRangeByScore(eq("market-data:MSFT"), eq(Double.NEGATIVE_INFINITY),
				anyDouble());
	}

	private MarketDataEvent event(String symbol) {
		Instant asOf = Instant.parse("2026-10-13T19:00:00Z");
		return new MarketDataEvent(symbol, 210.15, 210.1, 210.2, 4.0, "USD", 1.0, 0.5, 209.15, "cache",
				false, "open", asOf, asOf.plusSeconds(1));
	}
}

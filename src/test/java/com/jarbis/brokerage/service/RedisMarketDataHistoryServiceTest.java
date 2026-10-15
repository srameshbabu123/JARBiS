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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RedisMarketDataHistoryService Tests")
class RedisMarketDataHistoryServiceTest {

	private ObjectMapper objectMapper;
	private RedisMarketDataHistoryService historyService;

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ZSetOperations<String, String> zSetOperations;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		objectMapper = new ObjectMapper().findAndRegisterModules();

		MarketDataProperties properties = new MarketDataProperties();
		properties.getRedis().setKeyPrefix("market-data:");
		when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

		historyService = new RedisMarketDataHistoryService(redisTemplate, objectMapper, properties);
	}

	@Test
	@DisplayName("Should return events within an inclusive time range")
	void getHistoryReturnsEventsInRange() throws Exception {
		Instant from = Instant.parse("2026-10-13T19:00:00Z");
		Instant to = Instant.parse("2026-10-13T20:00:00Z");
		MarketDataEvent first = event("AAPL", from);
		MarketDataEvent second = event("AAPL", to);
		when(zSetOperations.rangeByScore("market-data:AAPL", from.toEpochMilli(), to.toEpochMilli()))
				.thenReturn(new LinkedHashSet<>(List.of(objectMapper.writeValueAsString(first),
						objectMapper.writeValueAsString(second))));

		List<MarketDataEvent> result = historyService.getHistory("aapl", from, to);

		assertEquals(List.of(first, second), result);
		verify(zSetOperations).rangeByScore(eq("market-data:AAPL"), eq((double) from.toEpochMilli()),
				eq((double) to.toEpochMilli()));
	}

	@Test
	@DisplayName("Should return an empty list when no events match")
	void getHistoryReturnsEmptyListWhenNoEventsMatch() {
		Instant from = Instant.parse("2026-10-13T19:00:00Z");
		Instant to = Instant.parse("2026-10-13T20:00:00Z");
		when(zSetOperations.rangeByScore("market-data:MSFT", from.toEpochMilli(), to.toEpochMilli()))
				.thenReturn(new LinkedHashSet<>());

		assertEquals(List.of(), historyService.getHistory("MSFT", from, to));
	}

	@Test
	@DisplayName("Should reject an invalid time range")
	void getHistoryRejectsFromAfterTo() {
		Instant from = Instant.parse("2026-10-13T20:00:00Z");
		Instant to = Instant.parse("2026-10-13T19:00:00Z");

		assertThrows(IllegalArgumentException.class, () -> historyService.getHistory("AAPL", from, to));
	}

	@Test
	@DisplayName("Should return the most recent event without removing it")
	void getLatestReturnsMostRecentEvent() throws Exception {
		MarketDataEvent latest = event("AAPL", Instant.parse("2026-10-13T20:00:00Z"));
		when(zSetOperations.reverseRange("market-data:AAPL", 0, 0))
				.thenReturn(new LinkedHashSet<>(List.of(objectMapper.writeValueAsString(latest))));

		Optional<MarketDataEvent> result = historyService.getLatest("aapl");

		assertEquals(Optional.of(latest), result);
		verify(zSetOperations).reverseRange("market-data:AAPL", 0, 0);
	}

	@Test
	@DisplayName("Should return empty when no latest event exists")
	void getLatestReturnsEmptyWhenNoEventExists() {
		when(zSetOperations.reverseRange("market-data:NVDA", 0, 0)).thenReturn(new LinkedHashSet<>());

		assertEquals(Optional.empty(), historyService.getLatest("NVDA"));
	}

	private MarketDataEvent event(String symbol, Instant timestamp) {
		return new MarketDataEvent(symbol, 210.15, 210.1, 210.2, 4.0, "USD", 1.0, 0.5, 209.15, "cache",
				false, "open", timestamp, timestamp.plusSeconds(1));
	}
}

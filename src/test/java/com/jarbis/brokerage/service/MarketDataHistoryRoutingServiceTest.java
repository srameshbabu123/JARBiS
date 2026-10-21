package com.jarbis.brokerage.service;

import com.jarbis.brokerage.marketdata.MarketDataEvent;
import com.jarbis.brokerage.marketdata.MarketHistoryRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MarketDataHistoryRoutingService Tests")
class MarketDataHistoryRoutingServiceTest {

	private RedisMarketDataHistoryService redisHistoryService;
	private MarketDataCandlesHistoryService candlesHistoryService;
	private MarketDataRangeWindowResolver rangeWindowResolver;
	private MarketDataHistoryRoutingService routingService;

	@BeforeEach
	void setUp() {
		redisHistoryService = mock(RedisMarketDataHistoryService.class);
		candlesHistoryService = mock(MarketDataCandlesHistoryService.class);
		rangeWindowResolver = mock(MarketDataRangeWindowResolver.class);
		routingService = new MarketDataHistoryRoutingService(redisHistoryService, candlesHistoryService,
				rangeWindowResolver);
	}

	@Test
	@DisplayName("Should use Redis history for 1d and 1w ranges")
	void getHistoryUsesRedisForShortRanges() {
		Instant from = Instant.parse("2026-10-19T20:00:00Z");
		Instant to = Instant.parse("2026-10-20T20:00:00Z");
		List<MarketDataEvent> events = List.of(event("AAPL", to));
		when(rangeWindowResolver.resolve(MarketHistoryRange.ONE_DAY, to))
				.thenReturn(new MarketDataRangeWindowResolver.RangeWindow(from, to));
		when(redisHistoryService.getHistory("AAPL", from, to)).thenReturn(events);

		List<MarketDataEvent> actual = routingService.getHistory("AAPL", MarketHistoryRange.ONE_DAY, to);

		assertEquals(events, actual);
		verify(redisHistoryService).getHistory("AAPL", from, to);
		verify(candlesHistoryService, never()).getHistory(any(), any(), any(), any());
	}

	@Test
	@DisplayName("Should use candles history for long ranges")
	void getHistoryUsesCandlesForLongRanges() {
		Instant from = Instant.parse("2025-10-20T20:00:00Z");
		Instant to = Instant.parse("2026-10-20T20:00:00Z");
		List<MarketDataEvent> events = List.of(event("AAPL", to));
		when(rangeWindowResolver.resolve(MarketHistoryRange.ONE_YEAR, to))
				.thenReturn(new MarketDataRangeWindowResolver.RangeWindow(from, to));
		when(candlesHistoryService.getHistory("AAPL", MarketHistoryRange.ONE_YEAR, from, to)).thenReturn(events);

		List<MarketDataEvent> actual = routingService.getHistory("AAPL", MarketHistoryRange.ONE_YEAR, to);

		assertEquals(events, actual);
		verify(candlesHistoryService).getHistory("AAPL", MarketHistoryRange.ONE_YEAR, from, to);
		verify(redisHistoryService, never()).getHistory(any(), any(), any());
	}

	private MarketDataEvent event(String symbol, Instant asOf) {
		return new MarketDataEvent(symbol, 210.15, null, null, null, "USD", null, null, null, "cache", false,
				"historical", asOf, asOf.plusSeconds(1));
	}
}
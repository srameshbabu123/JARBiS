package com.jarbis.brokerage.service;

import com.jarbis.brokerage.client.FauxnanceMarketDataApiClient;
import com.jarbis.brokerage.client.FauxnanceMarketDataApiClient.Candle;
import com.jarbis.brokerage.client.FauxnanceMarketDataApiClient.CandlesData;
import com.jarbis.brokerage.marketdata.MarketDataEvent;
import com.jarbis.brokerage.marketdata.MarketHistoryRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MarketDataCandlesHistoryService Tests")
class MarketDataCandlesHistoryServiceTest {

	private FauxnanceMarketDataApiClient apiClient;
	private MarketDataCandlesCacheService cacheService;
	private MarketDataCandlesHistoryService candlesHistoryService;

	@BeforeEach
	void setUp() {
		apiClient = mock(FauxnanceMarketDataApiClient.class);
		cacheService = mock(MarketDataCandlesCacheService.class);
		candlesHistoryService = new MarketDataCandlesHistoryService(apiClient, cacheService);
	}

	@Test
	@DisplayName("Should return cached candles history for long ranges")
	void getHistoryReturnsCachedEvents() {
		Instant from = Instant.parse("2026-09-20T00:00:00Z");
		Instant to = Instant.parse("2026-10-20T00:00:00Z");
		List<MarketDataEvent> cached = List.of(event("AAPL", 200.0, to));
		when(cacheService.get("AAPL", MarketHistoryRange.ONE_MONTH, LocalDate.parse("2026-10-20")))
				.thenReturn(Optional.of(cached));

		List<MarketDataEvent> actual = candlesHistoryService.getHistory("aapl", MarketHistoryRange.ONE_MONTH, from, to);

		assertEquals(cached, actual);
		verify(apiClient, never()).getCandles(any(), any(), any());
	}

	@Test
	@DisplayName("Should fetch candles, map them, and cache when cache misses")
	void getHistoryFetchesAndCachesOnMiss() {
		Instant from = Instant.parse("2026-09-20T00:00:00Z");
		Instant to = Instant.parse("2026-10-20T00:00:00Z");
		when(cacheService.get("AAPL", MarketHistoryRange.ONE_MONTH, LocalDate.parse("2026-10-20")))
				.thenReturn(Optional.empty());
		when(apiClient.getCandles("AAPL", LocalDate.parse("2026-09-20"), LocalDate.parse("2026-10-20")))
				.thenReturn(new CandlesData("AAPL", "1d", "USD", List.of(
						new Candle(LocalDate.parse("2026-10-18"), 100.0, 101.0, 99.0, 100.0, 100.5, 1000L, false),
						new Candle(LocalDate.parse("2026-10-19"), 101.0, 102.0, 100.0, 101.5, null, 1200L, false))));

		List<MarketDataEvent> actual = candlesHistoryService.getHistory("AAPL", MarketHistoryRange.ONE_MONTH, from, to);

		assertEquals(2, actual.size());
		assertEquals(100.5, actual.get(0).price());
		assertEquals(101.5, actual.get(1).price());
		assertEquals(1.0, actual.get(1).change());
		verify(cacheService).put(eq("AAPL"), eq(MarketHistoryRange.ONE_MONTH), eq(LocalDate.parse("2026-10-20")),
				eq(actual), any());
	}

	@Test
	@DisplayName("Should reject short ranges for candles history")
	void getHistoryRejectsShortRange() {
		assertThrows(IllegalArgumentException.class, () -> candlesHistoryService.getHistory("AAPL",
				MarketHistoryRange.ONE_DAY, Instant.parse("2026-10-19T00:00:00Z"), Instant.parse("2026-10-20T00:00:00Z")));
	}

	private MarketDataEvent event(String symbol, double price, Instant asOf) {
		return new MarketDataEvent(symbol, price, null, null, null, "USD", null, null, null, "cache", false,
				"historical", asOf, asOf.plusSeconds(1));
	}
}
package com.jarbis.brokerage.controller;

import com.jarbis.brokerage.exception.MarketDataUnavailableException;
import com.jarbis.brokerage.marketdata.MarketDataEvent;
import com.jarbis.brokerage.marketdata.MarketHistoryRange;
import com.jarbis.brokerage.marketdata.MarketSymbolMetadata;
import com.jarbis.brokerage.service.MarketDataHistoryRoutingService;
import com.jarbis.brokerage.service.MarketMetadataService;
import com.jarbis.brokerage.service.RedisMarketDataHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MarketDataController Tests")
class MarketDataControllerTest {

	private RedisMarketDataHistoryService marketDataHistoryService;
	private MarketDataHistoryRoutingService marketDataHistoryRoutingService;
	private MarketMetadataService marketMetadataService;
	private MarketDataController marketDataController;

	@BeforeEach
	void setUp() {
		marketDataHistoryService = mock(RedisMarketDataHistoryService.class);
		marketDataHistoryRoutingService = mock(MarketDataHistoryRoutingService.class);
		marketMetadataService = mock(MarketMetadataService.class);
		marketDataController = new MarketDataController(marketDataHistoryService, marketDataHistoryRoutingService,
				marketMetadataService);
	}

	@Test
	@DisplayName("Should return the latest market-data event")
	void getLatestReturnsEvent() {
		MarketDataEvent event = event("AAPL");
		when(marketDataHistoryService.getLatest("AAPL")).thenReturn(Optional.of(event));

		ResponseEntity<MarketDataEvent> response = marketDataController.getLatest("AAPL");

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(event, response.getBody());
	}

	@Test
	@DisplayName("Should fail when latest market data is unavailable")
	void getLatestFailsWhenNoEventExists() {
		when(marketDataHistoryService.getLatest("AAPL")).thenReturn(Optional.empty());

		assertThrows(MarketDataUnavailableException.class, () -> marketDataController.getLatest("AAPL"));
	}

	@Test
	@DisplayName("Should return historical market-data events")
	void getHistoryReturnsEvents() {
		Instant to = Instant.parse("2026-10-13T20:00:00Z");
		List<MarketDataEvent> events = List.of(event("AAPL"));
		when(marketDataHistoryRoutingService.getHistory("AAPL", MarketHistoryRange.ONE_WEEK, to)).thenReturn(events);

		ResponseEntity<List<MarketDataEvent>> response = marketDataController.getHistory("AAPL", "1w", to);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(events, response.getBody());
	}

	@Test
	@DisplayName("Should reject unsupported market-data ranges")
	void getHistoryRejectsUnsupportedRange() {
		assertThrows(IllegalArgumentException.class,
				() -> marketDataController.getHistory("AAPL", "10y", Instant.parse("2026-10-13T20:00:00Z")));
	}

	@Test
	@DisplayName("Should return market metadata entries")
	void getMetadataReturnsEntries() {
		List<MarketSymbolMetadata> metadata = List.of(new MarketSymbolMetadata("AAPL", "Technology", 100L));
		when(marketMetadataService.getMetadata(List.of("AAPL", "MSFT"))).thenReturn(metadata);

		ResponseEntity<List<MarketSymbolMetadata>> response = marketDataController.getMetadata("AAPL,MSFT");

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(metadata, response.getBody());
	}

	private MarketDataEvent event(String symbol) {
		Instant timestamp = Instant.parse("2026-10-13T20:00:00Z");
		return new MarketDataEvent(symbol, 210.15, 210.1, 210.2, 4.0, "USD", 1.0, 0.5, 209.15, "cache",
				false, "open", timestamp, timestamp.plusSeconds(1));
	}
}

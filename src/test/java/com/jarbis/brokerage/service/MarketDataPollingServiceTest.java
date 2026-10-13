package com.jarbis.brokerage.service;

import com.jarbis.brokerage.client.FauxnanceMarketDataApiClient;
import com.jarbis.brokerage.client.FauxnanceMarketDataApiClient.BatchQuoteItem;
import com.jarbis.brokerage.client.FauxnanceMarketDataApiClient.Quote;
import com.jarbis.brokerage.marketdata.MarketDataEvent;
import com.jarbis.brokerage.marketdata.MarketDataProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@DisplayName("MarketDataPollingService Tests")
class MarketDataPollingServiceTest {

	private MarketDataPollingService pollingService;

	@Mock
	private MarketDataSymbolProvider symbolProvider;

	@Mock
	private FauxnanceMarketDataApiClient marketDataApiClient;

	@Mock
	private MarketDataPersistenceService marketDataPersistenceService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		MarketDataProperties properties = new MarketDataProperties();
		properties.setBatchSize(2);
		pollingService = new MarketDataPollingService(symbolProvider, marketDataApiClient, marketDataPersistenceService,
				properties);
	}

	@Test
	@DisplayName("Should fetch symbols in batches and create market data events")
	void pollOnceCreatesEventsInBatches() {
		when(symbolProvider.getSymbols()).thenReturn(List.of("AAPL", "MSFT", "NVDA"));
		when(marketDataApiClient.getQuotes(List.of("AAPL", "MSFT"))).thenReturn(List.of(
				new BatchQuoteItem("AAPL", "cache", false, quote("AAPL", 210.15), null)));
		when(marketDataApiClient.getQuotes(List.of("NVDA"))).thenReturn(List.of(
				new BatchQuoteItem("NVDA", "cache", false, quote("NVDA", 128.75), null)));

		List<MarketDataEvent> events = pollingService.pollOnce();

		assertEquals(List.of("AAPL", "NVDA"), events.stream().map(MarketDataEvent::symbol).toList());
		assertEquals(210.15, events.get(0).price());
	}

	@Test
	@DisplayName("Should skip API items containing an error")
	void pollOnceSkipsErroredItems() {
		when(symbolProvider.getSymbols()).thenReturn(List.of("INVALID"));
		when(marketDataApiClient.getQuotes(List.of("INVALID"))).thenReturn(List.of(new BatchQuoteItem("INVALID",
				null, null, null, new FauxnanceMarketDataApiClient.QuoteError("SYMBOL_NOT_FOUND", "Unknown symbol"))));

		assertEquals(List.of(), pollingService.pollOnce());
	}

	@Test
	@DisplayName("Should persist each event after a scheduled poll")
	void scheduledPollPersistsEvents() {
		when(symbolProvider.getSymbols()).thenReturn(List.of("AAPL"));
		when(marketDataApiClient.getQuotes(List.of("AAPL"))).thenReturn(List.of(
				new BatchQuoteItem("AAPL", "cache", false, quote("AAPL", 210.15), null)));

		pollingService.pollMarketData();

		org.mockito.Mockito.verify(marketDataPersistenceService)
				.persist(org.mockito.ArgumentMatchers.any(MarketDataEvent.class));
	}

	private Quote quote(String symbol, double price) {
		return new Quote(symbol, price, price - 0.05, price + 0.05, 4.0, "USD", 1.0, 0.5, price - 1.0,
				Instant.parse("2026-10-13T19:00:00Z"), "open");
	}
}

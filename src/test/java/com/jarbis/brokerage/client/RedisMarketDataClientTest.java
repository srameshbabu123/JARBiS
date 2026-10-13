package com.jarbis.brokerage.client;

import com.jarbis.brokerage.entity.Asset;
import com.jarbis.brokerage.exception.MarketDataUnavailableException;
import com.jarbis.brokerage.marketdata.MarketDataEvent;
import com.jarbis.brokerage.service.RedisMarketDataHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RedisMarketDataClient Tests")
class RedisMarketDataClientTest {

	private RedisMarketDataClient marketDataClient;

	@Mock
	private RedisMarketDataHistoryService marketDataHistoryService;

	@Mock
	private Asset asset;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		marketDataClient = new RedisMarketDataClient(marketDataHistoryService);
	}

	@Test
	@DisplayName("Should return the latest Redis price for an asset symbol")
	void getCurrentPriceReturnsLatestRedisPrice() {
		MarketDataEvent event = event(210.15);
		when(asset.getName()).thenReturn("AAPL");
		when(marketDataHistoryService.getLatest("AAPL")).thenReturn(Optional.of(event));

		assertEquals(210.15, marketDataClient.getCurrentPrice(asset));
		verify(marketDataHistoryService).getLatest("AAPL");
	}

	@Test
	@DisplayName("Should fail when Redis has no latest price")
	void getCurrentPriceFailsWhenNoLatestPriceExists() {
		when(asset.getName()).thenReturn("AAPL");
		when(marketDataHistoryService.getLatest("AAPL")).thenReturn(Optional.empty());

		assertThrows(MarketDataUnavailableException.class, () -> marketDataClient.getCurrentPrice(asset));
	}

	@Test
	@DisplayName("Should fail for an asset without a symbol")
	void getCurrentPriceFailsWhenAssetSymbolIsMissing() {
		when(asset.getName()).thenReturn(" ");

		assertThrows(MarketDataUnavailableException.class, () -> marketDataClient.getCurrentPrice(asset));
	}

	private MarketDataEvent event(double price) {
		Instant timestamp = Instant.parse("2026-10-13T20:00:00Z");
		return new MarketDataEvent("AAPL", price, price - 0.05, price + 0.05, 4.0, "USD", 1.0, 0.5,
				price - 1.0, "cache", false, "open", timestamp, timestamp.plusSeconds(1));
	}
}

package com.jarbis.brokerage.service;

import com.jarbis.brokerage.client.FauxnanceMarketDataApiClient;
import com.jarbis.brokerage.client.FauxnanceMarketDataApiClient.BatchQuoteItem;
import com.jarbis.brokerage.client.FauxnanceMarketDataApiClient.Quote;
import com.jarbis.brokerage.marketdata.MarketDataEvent;
import com.jarbis.brokerage.marketdata.MarketDataProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "brokerage.market-data.polling", name = "enabled", havingValue = "true")
public class MarketDataPollingService {

	private static final Logger LOGGER = LoggerFactory.getLogger(MarketDataPollingService.class);

	private final MarketDataSymbolProvider symbolProvider;
	private final FauxnanceMarketDataApiClient marketDataApiClient;
	private final MarketDataPersistenceService marketDataPersistenceService;
	private final int batchSize;

	public MarketDataPollingService(MarketDataSymbolProvider symbolProvider,
			FauxnanceMarketDataApiClient marketDataApiClient, MarketDataPersistenceService marketDataPersistenceService,
			MarketDataProperties marketDataProperties) {
		if (marketDataProperties.getBatchSize() <= 0 || marketDataProperties.getBatchSize() > 25) {
			throw new IllegalArgumentException("Market data batch size must be between 1 and 25");
		}

		this.symbolProvider = symbolProvider;
		this.marketDataApiClient = marketDataApiClient;
		this.marketDataPersistenceService = marketDataPersistenceService;
		this.batchSize = marketDataProperties.getBatchSize();
	}

	@Scheduled(fixedDelayString = "${brokerage.market-data.polling.fixed-delay-ms:15000}",
			initialDelayString = "${brokerage.market-data.polling.initial-delay-ms:5000}")
	public void pollMarketData() {
		List<MarketDataEvent> events = pollOnce();
		events.forEach(marketDataPersistenceService::persist);
		LOGGER.info("Market data poll completed: created {} events", events.size());
	}

	public List<MarketDataEvent> pollOnce() {
		List<String> symbols = symbolProvider.getSymbols();
		if (symbols.isEmpty()) {
			LOGGER.debug("Skipping market data poll because no symbols are configured");
			return List.of();
		}

		List<MarketDataEvent> events = new ArrayList<>();
		for (List<String> symbolBatch : partition(symbols)) {
			for (BatchQuoteItem quoteItem : marketDataApiClient.getQuotes(symbolBatch)) {
				toEvent(quoteItem).ifPresent(events::add);
			}
		}
		return events;
	}

	private java.util.Optional<MarketDataEvent> toEvent(BatchQuoteItem quoteItem) {
		if (quoteItem.error() != null) {
			LOGGER.warn("Skipping market data for {} because Fauxnance returned {}: {}", quoteItem.symbol(),
					quoteItem.error().code(), quoteItem.error().message());
			return java.util.Optional.empty();
		}

		Quote quote = quoteItem.quote();
		if (quote == null || quote.price() == null) {
			LOGGER.warn("Skipping market data for {} because the response did not contain a price",
					quoteItem.symbol());
			return java.util.Optional.empty();
		}

		return java.util.Optional.of(new MarketDataEvent(quote.symbol(), quote.price(), quote.bid(), quote.ask(),
				quote.spreadBps(), quote.currency(), quote.change(), quote.changePercent(), quote.previousClose(),
				quoteItem.source(), Boolean.TRUE.equals(quoteItem.stale()), quote.marketState(), quote.asOf(),
				Instant.now()));
	}

	private List<List<String>> partition(List<String> symbols) {
		List<List<String>> batches = new ArrayList<>();
		for (int startIndex = 0; startIndex < symbols.size(); startIndex += batchSize) {
			int endIndex = Math.min(startIndex + batchSize, symbols.size());
			batches.add(symbols.subList(startIndex, endIndex));
		}
		return batches;
	}
}

package com.jarbis.brokerage.service;

import com.jarbis.brokerage.client.FauxnanceMarketDataApiClient;
import com.jarbis.brokerage.client.FauxnanceMarketDataApiClient.Candle;
import com.jarbis.brokerage.client.FauxnanceMarketDataApiClient.CandlesData;
import com.jarbis.brokerage.marketdata.MarketDataEvent;
import com.jarbis.brokerage.marketdata.MarketHistoryRange;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class MarketDataCandlesHistoryService {

	private final FauxnanceMarketDataApiClient marketDataApiClient;
	private final MarketDataCandlesCacheService candlesCacheService;

	public MarketDataCandlesHistoryService(FauxnanceMarketDataApiClient marketDataApiClient,
			MarketDataCandlesCacheService candlesCacheService) {
		this.marketDataApiClient = marketDataApiClient;
		this.candlesCacheService = candlesCacheService;
	}

	public List<MarketDataEvent> getHistory(String symbol, MarketHistoryRange range, Instant from, Instant to) {
		if (range == null || range.isRedisBackedRange()) {
			throw new IllegalArgumentException("Candles history service supports only long ranges");
		}
		if (from == null || to == null) {
			throw new IllegalArgumentException("Candles history requires from and to timestamps");
		}

		String normalizedSymbol = normalizeSymbol(symbol);
		LocalDate fromDate = from.atZone(ZoneOffset.UTC).toLocalDate();
		LocalDate toDate = to.atZone(ZoneOffset.UTC).toLocalDate();

		return candlesCacheService.get(normalizedSymbol, range, toDate)
				.orElseGet(() -> fetchAndCache(normalizedSymbol, range, fromDate, toDate));
	}

	private List<MarketDataEvent> fetchAndCache(String symbol, MarketHistoryRange range, LocalDate from,
			LocalDate to) {
		CandlesData data = marketDataApiClient.getCandles(symbol, from, to);
		List<MarketDataEvent> events = toEvents(symbol, data);
		candlesCacheService.put(symbol, range, to, events, ttlForRange(range));
		return events;
	}

	private List<MarketDataEvent> toEvents(String fallbackSymbol, CandlesData data) {
		if (data.candles() == null || data.candles().isEmpty()) {
			return List.of();
		}

		String symbol = normalizeSymbol(data.symbol() != null ? data.symbol() : fallbackSymbol);
		Instant capturedAt = Instant.now();
		List<MarketDataEvent> events = new ArrayList<>();
		Double previousPrice = null;

		for (Candle candle : data.candles()) {
			if (candle == null || candle.date() == null) {
				continue;
			}

			Double price = candle.adjclose() != null ? candle.adjclose() : candle.close();
			if (price == null) {
				continue;
			}

			Double change = previousPrice != null ? price - previousPrice : null;
			Double changePercent = previousPrice != null && previousPrice != 0 ? (change / previousPrice) * 100.0
					: null;

			Instant asOf = candle.date().atStartOfDay(ZoneOffset.UTC).toInstant();
			events.add(new MarketDataEvent(symbol, price, null, null, null, data.currency(), change, changePercent,
					previousPrice, "fauxnance-candles", false, "historical", asOf, capturedAt));

			previousPrice = price;
		}

		return events;
	}

	private String normalizeSymbol(String symbol) {
		if (symbol == null || symbol.isBlank()) {
			throw new IllegalArgumentException("Market data symbol cannot be blank");
		}
		return symbol.trim().toUpperCase(Locale.ROOT);
	}

	private Duration ttlForRange(MarketHistoryRange range) {
		return switch (range) {
			case ONE_MONTH, THREE_MONTH -> Duration.ofMinutes(30);
			case SIX_MONTH, YEAR_TO_DATE, ONE_YEAR -> Duration.ofHours(2);
			case FIVE_YEAR -> Duration.ofHours(12);
			default -> Duration.ofMinutes(30);
		};
	}
}
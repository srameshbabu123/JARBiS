package com.jarbis.brokerage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarbis.brokerage.marketdata.MarketDataEvent;
import com.jarbis.brokerage.marketdata.MarketHistoryRange;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class MarketDataCandlesCacheService {

	private static final String KEY_PREFIX = "market-data:candles:";
	private static final TypeReference<List<MarketDataEvent>> EVENTS_TYPE = new TypeReference<>() {
	};

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	public MarketDataCandlesCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
	}

	public Optional<List<MarketDataEvent>> get(String symbol, MarketHistoryRange range, LocalDate toDate) {
		String payload = redisTemplate.opsForValue().get(cacheKey(symbol, range, toDate));
		if (payload == null || payload.isBlank()) {
			return Optional.empty();
		}

		try {
			return Optional.of(objectMapper.readValue(payload, EVENTS_TYPE));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to deserialize cached market-data candles", exception);
		}
	}

	public void put(String symbol, MarketHistoryRange range, LocalDate toDate, List<MarketDataEvent> events,
			Duration ttl) {
		if (events == null || ttl == null || ttl.isZero() || ttl.isNegative()) {
			return;
		}

		try {
			String payload = objectMapper.writeValueAsString(events);
			redisTemplate.opsForValue().set(cacheKey(symbol, range, toDate), payload, ttl);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to serialize market-data candles for cache", exception);
		}
	}

	private String cacheKey(String symbol, MarketHistoryRange range, LocalDate toDate) {
		if (symbol == null || symbol.isBlank()) {
			throw new IllegalArgumentException("Market data symbol is required");
		}
		if (range == null) {
			throw new IllegalArgumentException("Market data range is required");
		}
		if (toDate == null) {
			throw new IllegalArgumentException("Market data cache to-date is required");
		}

		return KEY_PREFIX + symbol.trim().toUpperCase(Locale.ROOT) + ":" + range.token() + ":" + toDate;
	}
}
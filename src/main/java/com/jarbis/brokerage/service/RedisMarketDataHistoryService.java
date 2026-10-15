package com.jarbis.brokerage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarbis.brokerage.marketdata.MarketDataEvent;
import com.jarbis.brokerage.marketdata.MarketDataProperties;
import com.jarbis.brokerage.marketdata.MarketDataRedisKey;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class RedisMarketDataHistoryService {

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final MarketDataProperties marketDataProperties;

	public RedisMarketDataHistoryService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
			MarketDataProperties marketDataProperties) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		this.marketDataProperties = marketDataProperties;
	}

	/**
	 * Returns all stored market-data events whose timestamp is within the inclusive range.
	 */
	public List<MarketDataEvent> getHistory(String symbol, Instant from, Instant to) {
		if (from == null || to == null) {
			throw new IllegalArgumentException("Market data history range must include from and to");
		}
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("Market data history from must not be after to");
		}

		String key = MarketDataRedisKey.forSymbol(marketDataProperties.getRedis().getKeyPrefix(), symbol);
		Set<String> payloads = redisTemplate.opsForZSet().rangeByScore(key, from.toEpochMilli(), to.toEpochMilli());
		if (payloads == null || payloads.isEmpty()) {
			return List.of();
		}

		return payloads.stream().map(this::deserialize).toList();
	}

	public Optional<MarketDataEvent> getLatest(String symbol) {
		String key = MarketDataRedisKey.forSymbol(marketDataProperties.getRedis().getKeyPrefix(), symbol);
		Set<String> payloads = redisTemplate.opsForZSet().reverseRange(key, 0, 0);
		if (payloads == null || payloads.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(deserialize(payloads.iterator().next()));
	}

	private MarketDataEvent deserialize(String payload) {
		try {
			return objectMapper.readValue(payload, MarketDataEvent.class);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to deserialize market data event from Redis", exception);
		}
	}
}

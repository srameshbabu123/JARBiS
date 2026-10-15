package com.jarbis.brokerage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarbis.brokerage.marketdata.MarketDataEvent;
import com.jarbis.brokerage.marketdata.MarketDataEventTimestamp;
import com.jarbis.brokerage.marketdata.MarketDataSnapshot;
import com.jarbis.brokerage.repository.MarketDataSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class MarketDataPostgresService {

	private final MarketDataSnapshotRepository snapshotRepository;
	private final ObjectMapper objectMapper;

	public MarketDataPostgresService(MarketDataSnapshotRepository snapshotRepository, ObjectMapper objectMapper) {
		this.snapshotRepository = snapshotRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public void storeSnapshot(MarketDataEvent event) {
		if (event == null || !StringUtils.hasText(event.symbol())) {
			throw new IllegalArgumentException("Market data event symbol cannot be blank");
		}

		try {
			Instant scoreTimestamp = MarketDataEventTimestamp.resolve(event);
			String symbol = event.symbol().trim().toUpperCase(Locale.ROOT);
			String payload = objectMapper.writeValueAsString(event);
			snapshotRepository.save(new MarketDataSnapshot(symbol, scoreTimestamp, payload));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to serialize market data event for PostgreSQL", exception);
		}
	}

	@Transactional(readOnly = true)
	public List<MarketDataEvent> findSnapshots(Instant from, Instant to) {
		if (from == null || to == null) {
			throw new IllegalArgumentException("Market data snapshot range must include from and to");
		}
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("Market data snapshot from must not be after to");
		}

		return snapshotRepository.findByScoreTimestampBetweenOrderByScoreTimestampAsc(from, to).stream()
				.map(MarketDataSnapshot::getPayload)
				.map(this::deserialize)
				.toList();
	}

	private MarketDataEvent deserialize(String payload) {
		try {
			return objectMapper.readValue(payload, MarketDataEvent.class);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to deserialize market data event from PostgreSQL", exception);
		}
	}
}

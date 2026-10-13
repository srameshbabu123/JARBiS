package com.jarbis.brokerage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarbis.brokerage.marketdata.MarketDataEvent;
import com.jarbis.brokerage.marketdata.MarketDataSnapshot;
import com.jarbis.brokerage.repository.MarketDataSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MarketDataPostgresService Tests")
class MarketDataPostgresServiceTest {

	private ObjectMapper objectMapper;
	private MarketDataPostgresService postgresService;

	@Mock
	private MarketDataSnapshotRepository snapshotRepository;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		objectMapper = new ObjectMapper().findAndRegisterModules();
		postgresService = new MarketDataPostgresService(snapshotRepository, objectMapper);
	}

	@Test
	@DisplayName("Should persist a serialized market-data snapshot")
	void storeSnapshotPersistsEvent() {
		MarketDataEvent event = event("aapl");

		postgresService.storeSnapshot(event);

		verify(snapshotRepository).save(any(MarketDataSnapshot.class));
	}

	@Test
	@DisplayName("Should restore stored events from PostgreSQL payloads")
	void findSnapshotsDeserializesEvents() throws Exception {
		Instant from = Instant.parse("2026-10-13T19:00:00Z");
		Instant to = Instant.parse("2026-10-13T20:00:00Z");
		MarketDataEvent event = event("AAPL");
		MarketDataSnapshot snapshot = new MarketDataSnapshot("AAPL", event.asOf(),
				objectMapper.writeValueAsString(event));
		when(snapshotRepository.findByScoreTimestampBetweenOrderByScoreTimestampAsc(from, to))
				.thenReturn(List.of(snapshot));

		assertEquals(List.of(event), postgresService.findSnapshots(from, to));
	}

	private MarketDataEvent event(String symbol) {
		Instant timestamp = Instant.parse("2026-10-13T19:30:00Z");
		return new MarketDataEvent(symbol, 210.15, 210.1, 210.2, 4.0, "USD", 1.0, 0.5, 209.15, "cache",
				false, "open", timestamp, timestamp.plusSeconds(1));
	}
}

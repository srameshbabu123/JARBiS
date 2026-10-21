package com.jarbis.brokerage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarbis.brokerage.repository.MarketDataSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MarketDataPostgresService Cleanup Tests")
class MarketDataPostgresServiceCleanupTest {

	private MarketDataSnapshotRepository snapshotRepository;
	private MarketDataPostgresService postgresService;

	@BeforeEach
	void setUp() {
		snapshotRepository = mock(MarketDataSnapshotRepository.class);
		postgresService = new MarketDataPostgresService(snapshotRepository, new ObjectMapper());
	}

	@Test
	@DisplayName("Should delete snapshots older than cutoff")
	void purgeSnapshotsBeforeDeletesRows() {
		Instant cutoff = Instant.parse("2026-10-20T00:00:00Z");
		when(snapshotRepository.deleteByScoreTimestampBefore(cutoff)).thenReturn(12L);

		long removed = postgresService.purgeSnapshotsBefore(cutoff);

		assertEquals(12L, removed);
		verify(snapshotRepository).deleteByScoreTimestampBefore(cutoff);
	}

	@Test
	@DisplayName("Should reject null cutoff")
	void purgeSnapshotsBeforeRejectsNullCutoff() {
		assertThrows(IllegalArgumentException.class, () -> postgresService.purgeSnapshotsBefore(null));
	}
}
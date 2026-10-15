package com.jarbis.brokerage.repository;

import com.jarbis.brokerage.marketdata.MarketDataSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MarketDataSnapshotRepository extends JpaRepository<MarketDataSnapshot, Long> {

	List<MarketDataSnapshot> findByScoreTimestampBetweenOrderByScoreTimestampAsc(Instant from, Instant to);
}

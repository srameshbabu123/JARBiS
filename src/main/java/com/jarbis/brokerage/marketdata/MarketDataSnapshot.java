package com.jarbis.brokerage.marketdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "market_data_snapshots", indexes = {
		@Index(name = "idx_market_data_snapshots_symbol_timestamp", columnList = "symbol, score_timestamp") })
public class MarketDataSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String symbol;

	@Column(name = "score_timestamp", nullable = false)
	private Instant scoreTimestamp;

	@Lob
	@Column(nullable = false)
	private String payload;

	protected MarketDataSnapshot() {
	}

	public MarketDataSnapshot(String symbol, Instant scoreTimestamp, String payload) {
		this.symbol = symbol;
		this.scoreTimestamp = scoreTimestamp;
		this.payload = payload;
	}

	public Long getId() {
		return id;
	}

	public String getSymbol() {
		return symbol;
	}

	public Instant getScoreTimestamp() {
		return scoreTimestamp;
	}

	public String getPayload() {
		return payload;
	}
}

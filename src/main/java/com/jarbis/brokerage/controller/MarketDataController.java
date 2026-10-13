package com.jarbis.brokerage.controller;

import com.jarbis.brokerage.exception.MarketDataUnavailableException;
import com.jarbis.brokerage.marketdata.MarketDataEvent;
import com.jarbis.brokerage.service.RedisMarketDataHistoryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/market-data")
public class MarketDataController {

	private final RedisMarketDataHistoryService marketDataHistoryService;

	public MarketDataController(RedisMarketDataHistoryService marketDataHistoryService) {
		this.marketDataHistoryService = marketDataHistoryService;
	}

	@GetMapping("/{symbol}/latest")
	public ResponseEntity<MarketDataEvent> getLatest(@PathVariable String symbol) {
		MarketDataEvent latest = marketDataHistoryService.getLatest(symbol)
				.orElseThrow(() -> new MarketDataUnavailableException(
						"No current market data available for symbol: " + symbol));
		return ResponseEntity.ok(latest);
	}

	@GetMapping("/{symbol}/history")
	public ResponseEntity<List<MarketDataEvent>> getHistory(@PathVariable String symbol,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
		return ResponseEntity.ok(marketDataHistoryService.getHistory(symbol, from, to));
	}
}

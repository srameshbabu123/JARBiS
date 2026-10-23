package com.jarbis.brokerage.controller;

import com.jarbis.brokerage.exception.MarketDataUnavailableException;
import com.jarbis.brokerage.marketdata.MarketDataEvent;
import com.jarbis.brokerage.marketdata.MarketHistoryRange;
import com.jarbis.brokerage.marketdata.MarketSymbolMetadata;
import com.jarbis.brokerage.service.MarketDataHistoryRoutingService;
import com.jarbis.brokerage.service.MarketMetadataService;
import com.jarbis.brokerage.service.RedisMarketDataHistoryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/market-data")
public class MarketDataController {

	private final RedisMarketDataHistoryService marketDataHistoryService;
	private final MarketDataHistoryRoutingService marketDataHistoryRoutingService;
	private final MarketMetadataService marketMetadataService;

	public MarketDataController(RedisMarketDataHistoryService marketDataHistoryService,
			MarketDataHistoryRoutingService marketDataHistoryRoutingService,
			MarketMetadataService marketMetadataService) {
		this.marketDataHistoryService = marketDataHistoryService;
		this.marketDataHistoryRoutingService = marketDataHistoryRoutingService;
		this.marketMetadataService = marketMetadataService;
	}

	@GetMapping("/{symbol}/latest")
	public ResponseEntity<MarketDataEvent> getLatest(@PathVariable String symbol) {
		MarketDataEvent latest = marketDataHistoryService.getLatest(symbol)
				.orElseThrow(() -> new MarketDataUnavailableException(
						"No current market data available for symbol: " + symbol));
		return ResponseEntity.ok(latest);
	}

	@GetMapping("/{symbol}/history")
	public ResponseEntity<List<MarketDataEvent>> getHistory(@PathVariable String symbol, @RequestParam String range,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
		MarketHistoryRange historyRange = MarketHistoryRange.parse(range);
		return ResponseEntity.ok(marketDataHistoryRoutingService.getHistory(symbol, historyRange, to));
	}

	@GetMapping("/metadata")
	public ResponseEntity<List<MarketSymbolMetadata>> getMetadata(@RequestParam String symbols) {
		List<String> symbolList = Arrays.stream(symbols.split(","))
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.toList();
		if (symbolList.isEmpty()) {
			throw new IllegalArgumentException("At least one symbol is required");
		}
		return ResponseEntity.ok(marketMetadataService.getMetadata(symbolList));
	}
}

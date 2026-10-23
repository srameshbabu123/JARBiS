package com.jarbis.brokerage.service;

import com.jarbis.brokerage.marketdata.MarketDataProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MarketDataSymbolProvider {

	public record MarketSymbolEntry(String symbol, String name) {
	}

	private final ResourceLoader resourceLoader;
	private final MarketDataProperties marketDataProperties;

	public MarketDataSymbolProvider(ResourceLoader resourceLoader, MarketDataProperties marketDataProperties) {
		this.resourceLoader = resourceLoader;
		this.marketDataProperties = marketDataProperties;
	}

	public List<String> getSymbols() {
		return getSymbolEntries().stream()
				.map(MarketSymbolEntry::symbol)
				.toList();
	}

	public List<MarketSymbolEntry> getSymbolEntries() {
		Resource resource = resourceLoader.getResource(marketDataProperties.getSymbolsFile());

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
			Map<String, MarketSymbolEntry> entries = new LinkedHashMap<>();
			reader.lines()
					.map(String::trim)
					.filter(line -> !line.isEmpty())
					.filter(line -> !line.startsWith("#"))
					.map(this::parseLine)
					.forEach(entry -> entries.putIfAbsent(entry.symbol(), entry));
			return List.copyOf(entries.values());
		} catch (IOException exception) {
			throw new IllegalStateException(
					"Unable to read market data symbols from " + marketDataProperties.getSymbolsFile(), exception);
		}
	}

	public Map<String, String> getSymbolNameMap() {
		return getSymbolEntries().stream()
				.collect(LinkedHashMap::new, (map, entry) -> map.put(entry.symbol(), entry.name()), Map::putAll);
	}

	private MarketSymbolEntry parseLine(String rawLine) {
		String[] parts = rawLine.split("\\|", 2);
		String symbol = parts[0].trim().toUpperCase(Locale.ROOT);
		String name = parts.length > 1 ? parts[1].trim() : "";
		if (name.isBlank()) {
			name = symbol;
		}
		return new MarketSymbolEntry(symbol, name);
	}
}

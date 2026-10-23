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

	public record SymbolEntry(String symbol, String companyName) {
		public String assetName() {
			if (companyName == null || companyName.isBlank() || companyName.equalsIgnoreCase(symbol)) {
				return symbol;
			}
			return companyName + " (" + symbol + ")";
		}
	}

	private final ResourceLoader resourceLoader;
	private final MarketDataProperties marketDataProperties;

	public MarketDataSymbolProvider(ResourceLoader resourceLoader, MarketDataProperties marketDataProperties) {
		this.resourceLoader = resourceLoader;
		this.marketDataProperties = marketDataProperties;
	}

	public List<String> getSymbols() {
		return getSymbolEntries().stream().map(SymbolEntry::symbol).toList();
	}

	public List<SymbolEntry> getSymbolEntries() {
		Resource resource = resourceLoader.getResource(marketDataProperties.getSymbolsFile());

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
			Map<String, SymbolEntry> bySymbol = new LinkedHashMap<>();
			reader.lines()
					.map(String::trim)
					.filter(line -> !line.isEmpty())
					.filter(line -> !line.startsWith("#"))
					.map(this::toSymbolEntry)
					.forEach(entry -> bySymbol.putIfAbsent(entry.symbol(), entry));
			return bySymbol.values().stream().toList();
		} catch (IOException exception) {
			throw new IllegalStateException(
					"Unable to read market data symbols from " + marketDataProperties.getSymbolsFile(), exception);
		}
	}

	private SymbolEntry toSymbolEntry(String line) {
		String[] parts = line.split("\\|", 2);
		String symbol = parts[0].trim().toUpperCase(Locale.ROOT);
		String companyName = parts.length > 1 ? parts[1].trim() : symbol;
		if (companyName.isBlank()) {
			companyName = symbol;
		}
		return new SymbolEntry(symbol, companyName);
	}
}

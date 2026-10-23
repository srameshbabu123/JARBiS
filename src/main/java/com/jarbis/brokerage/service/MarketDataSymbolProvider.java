package com.jarbis.brokerage.service;

import com.jarbis.brokerage.marketdata.MarketDataProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
public class MarketDataSymbolProvider {

	private final ResourceLoader resourceLoader;
	private final MarketDataProperties marketDataProperties;

	public MarketDataSymbolProvider(ResourceLoader resourceLoader, MarketDataProperties marketDataProperties) {
		this.resourceLoader = resourceLoader;
		this.marketDataProperties = marketDataProperties;
	}

	public List<String> getSymbols() {
		Resource resource = resourceLoader.getResource(marketDataProperties.getSymbolsFile());

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
			return reader.lines()
					.map(String::trim)
					.filter(line -> !line.isEmpty())
					.filter(line -> !line.startsWith("#"))
					.map(line -> line.toUpperCase(Locale.ROOT))
					.distinct()
					.toList();
		} catch (IOException exception) {
			throw new IllegalStateException(
					"Unable to read market data symbols from " + marketDataProperties.getSymbolsFile(), exception);
		}
	}
}

package com.jarbis.brokerage.service;

import com.jarbis.brokerage.marketdata.MarketSymbolMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class MarketMetadataService {

	private final RestClient restClient;
	private final MarketDataSymbolProvider symbolProvider;

	public MarketMetadataService(RestClient.Builder restClientBuilder,
			@Value("${brokerage.analytics.service-url:http://analytics:8082}") String analyticsServiceUrl,
			MarketDataSymbolProvider symbolProvider) {
		this.restClient = restClientBuilder.baseUrl(analyticsServiceUrl).build();
		this.symbolProvider = symbolProvider;
	}

	public List<MarketSymbolMetadata> getMetadata(List<String> symbols) {
		List<String> normalized = symbols == null ? List.of() : symbols.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(s -> !s.isBlank())
				.map(String::toUpperCase)
				.distinct()
				.toList();

		if (normalized.isEmpty()) {
			return List.of();
		}

		List<MarketSymbolMetadata> response;
		try {
			response = restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/public/market-metadata")
							.queryParam("symbols", String.join(",", normalized))
							.build())
					.retrieve()
					.body(new ParameterizedTypeReference<List<MarketSymbolMetadata>>() {
					});
		} catch (RestClientException exception) {
			response = Collections.emptyList();
		}

		Map<String, String> symbolNameMap = symbolProvider.getSymbolNameMap();
		Map<String, MarketSymbolMetadata> bySymbol = new LinkedHashMap<>();
		for (MarketSymbolMetadata item : response) {
			if (item == null || item.symbol() == null) {
				continue;
			}
			String key = item.symbol().trim().toUpperCase();
			if (!key.isBlank()) {
				bySymbol.put(key, item);
			}
		}

		return normalized.stream()
				.map(symbol -> {
					MarketSymbolMetadata item = bySymbol.get(symbol);
					String name = symbolNameMap.getOrDefault(symbol, symbol);
					if (item == null) {
						return new MarketSymbolMetadata(symbol, name, "Unknown", 0L);
					}

					String sector = item.sector() == null || item.sector().isBlank() ? "Unknown" : item.sector();
					Long marketCap = item.marketCap() == null ? 0L : Math.max(0L, item.marketCap());
					String resolvedName = item.name() == null || item.name().isBlank() ? name : item.name();
					return new MarketSymbolMetadata(symbol, resolvedName, sector, marketCap);
				})
				.toList();
	}
}
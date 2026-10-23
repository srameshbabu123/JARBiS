package com.jarbis.brokerage.service;

import com.jarbis.brokerage.marketdata.MarketSymbolMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
public class MarketMetadataService {

	private final RestClient restClient;

	public MarketMetadataService(RestClient.Builder restClientBuilder,
			@Value("${brokerage.analytics.service-url:http://analytics:8082}") String analyticsServiceUrl) {
		this.restClient = restClientBuilder.baseUrl(analyticsServiceUrl).build();
	}

	public List<MarketSymbolMetadata> getMetadata(List<String> symbols) {
		List<String> normalized = symbols == null ? List.of() : symbols.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(s -> !s.isBlank())
				.map(String::toUpperCase)
				.distinct()
				.limit(50)
				.toList();

		if (normalized.isEmpty()) {
			return List.of();
		}

		try {
			List<MarketSymbolMetadata> response = restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/public/market-metadata")
							.queryParam("symbols", String.join(",", normalized))
							.build())
					.retrieve()
					.body(new ParameterizedTypeReference<List<MarketSymbolMetadata>>() {
					});
			return response == null ? Collections.emptyList() : response;
		} catch (RestClientException exception) {
			return Collections.emptyList();
		}
	}
}
package com.jarbis.brokerage.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jarbis.brokerage.exception.MarketDataUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.List;

@Component
public class FauxnanceMarketDataApiClient {

	private static final int MAX_SYMBOLS_PER_REQUEST = 25;

	private final RestClient restClient;
	private final String apiKey;

	public FauxnanceMarketDataApiClient(RestClient.Builder restClientBuilder,
			@Value("${brokerage.market-data.base-url}") String baseUrl,
			@Value("${brokerage.market-data.api-key}") String apiKey) {
		this.restClient = restClientBuilder.baseUrl(baseUrl).build();
		this.apiKey = apiKey;
	}

	public List<BatchQuoteItem> getQuotes(List<String> symbols) {
		validateSymbols(symbols);

		try {
			BatchQuotesResponse response = restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/quotes")
							.queryParam("symbols", String.join(",", symbols))
							.build())
					.header("X-Api-Key", apiKey)
					.retrieve()
					.body(BatchQuotesResponse.class);

			if (response == null || response.data() == null || response.data().quotes() == null) {
				throw new MarketDataUnavailableException("Fauxnance returned an empty quotes response");
			}

			return response.data().quotes();
		} catch (RestClientResponseException exception) {
			throw new MarketDataUnavailableException(
					"Fauxnance request failed with HTTP status " + exception.getStatusCode().value(), exception);
		} catch (RestClientException exception) {
			throw new MarketDataUnavailableException("Fauxnance request failed: " + exception.getMessage(), exception);
		}
	}

	private void validateSymbols(List<String> symbols) {
		if (!StringUtils.hasText(apiKey)) {
			throw new MarketDataUnavailableException("Market data API key is not configured");
		}
		if (symbols == null || symbols.isEmpty()) {
			throw new IllegalArgumentException("At least one market data symbol is required");
		}
		if (symbols.size() > MAX_SYMBOLS_PER_REQUEST) {
			throw new IllegalArgumentException("Fauxnance supports at most " + MAX_SYMBOLS_PER_REQUEST
					+ " symbols per quotes request");
		}
		if (symbols.stream().anyMatch(symbol -> !StringUtils.hasText(symbol))) {
			throw new IllegalArgumentException("Market data symbols cannot be blank");
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record BatchQuotesResponse(QuotesData data) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record QuotesData(List<BatchQuoteItem> quotes) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record BatchQuoteItem(String symbol, String source, Boolean stale, Quote quote, QuoteError error) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Quote(String symbol, Double price, Double bid, Double ask, Double spreadBps, String currency,
			Double change, Double changePercent, Double previousClose, Instant asOf, String marketState) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record QuoteError(String code, String message) {
	}
}

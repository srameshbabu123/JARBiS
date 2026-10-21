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
import java.time.LocalDate;
import java.util.List;

@Component
public class FauxnanceMarketDataApiClient {

	private static final int MAX_SYMBOLS_PER_REQUEST = 25;

	private final RestClient restClient;
	private final String pollingApiKey;
	private final String candlesApiKey;

	public FauxnanceMarketDataApiClient(RestClient.Builder restClientBuilder,
			@Value("${brokerage.market-data.base-url}") String baseUrl,
			@Value("${brokerage.market-data.polling-api-key:${brokerage.market-data.api-key:}}") String pollingApiKey,
			@Value("${brokerage.market-data.candles-api-key:${brokerage.market-data.api-key:}}") String candlesApiKey) {
		this.restClient = restClientBuilder.baseUrl(baseUrl).build();
		this.pollingApiKey = pollingApiKey;
		this.candlesApiKey = candlesApiKey;
	}

	public List<BatchQuoteItem> getQuotes(List<String> symbols) {
		validateSymbols(symbols);

		try {
			BatchQuotesResponse response = restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/quotes")
							.queryParam("symbols", String.join(",", symbols))
							.build())
					.header("X-Api-Key", pollingApiKey)
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

	public CandlesData getCandles(String symbol, LocalDate from, LocalDate to) {
		validateSymbol(symbol);
		if (from == null || to == null) {
			throw new IllegalArgumentException("Candles request requires from and to dates");
		}
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("Candles from date must not be after to date");
		}

		try {
			CandlesResponse response = restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/candles/{symbol}")
							.queryParam("from", from)
							.queryParam("to", to)
							.queryParam("interval", "1d")
							.build(symbol))
					.header("X-Api-Key", candlesApiKey)
					.retrieve()
					.body(CandlesResponse.class);

			if (response == null || response.data() == null || response.data().candles() == null) {
				throw new MarketDataUnavailableException("Fauxnance returned an empty candles response");
			}

			return response.data();
		} catch (RestClientResponseException exception) {
			throw new MarketDataUnavailableException(
					"Fauxnance candles request failed with HTTP status " + exception.getStatusCode().value(), exception);
		} catch (RestClientException exception) {
			throw new MarketDataUnavailableException(
					"Fauxnance candles request failed: " + exception.getMessage(), exception);
		}
	}

	private void validateSymbols(List<String> symbols) {
		if (!StringUtils.hasText(pollingApiKey)) {
			throw new MarketDataUnavailableException("Polling market data API key is not configured");
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

	private void validateSymbol(String symbol) {
		if (!StringUtils.hasText(candlesApiKey)) {
			throw new MarketDataUnavailableException("Candles market data API key is not configured");
		}
		if (!StringUtils.hasText(symbol)) {
			throw new IllegalArgumentException("Market data symbol cannot be blank");
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

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record CandlesResponse(CandlesData data) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record CandlesData(String symbol, String interval, String currency, List<Candle> candles) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Candle(LocalDate date, Double open, Double high, Double low, Double close, Double adjclose,
			Long volume, Boolean synthetic) {
	}
}

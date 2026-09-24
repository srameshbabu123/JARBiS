package com.jarbis.brokerage.exception;

/**
 * Exception thrown when market data cannot be retrieved for an asset.
 */
public class MarketDataUnavailableException extends RuntimeException {

    public MarketDataUnavailableException(String message) {
        super(message);
    }

    public MarketDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

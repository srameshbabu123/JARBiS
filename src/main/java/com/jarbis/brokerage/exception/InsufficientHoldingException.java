package com.jarbis.brokerage.exception;

/**
 * Exception thrown when an account attempts to sell more of an asset than it holds.
 */
public class InsufficientHoldingException extends RuntimeException {

    public InsufficientHoldingException(String message) {
        super(message);
    }

    public InsufficientHoldingException(String message, Throwable cause) {
        super(message, cause);
    }
}

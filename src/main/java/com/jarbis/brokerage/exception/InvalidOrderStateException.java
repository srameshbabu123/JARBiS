package com.jarbis.brokerage.exception;

/**
 * Exception thrown when an order lifecycle transition is not allowed.
 */
public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(String message) {
        super(message);
    }

    public InvalidOrderStateException(String message, Throwable cause) {
        super(message, cause);
    }
}

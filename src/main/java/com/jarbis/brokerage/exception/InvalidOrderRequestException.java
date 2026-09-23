package com.jarbis.brokerage.exception;

/**
 * Exception thrown when an order request contains invalid or incomplete input.
 */
public class InvalidOrderRequestException extends RuntimeException {

    public InvalidOrderRequestException(String message) {
        super(message);
    }

    public InvalidOrderRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}

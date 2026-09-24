package com.jarbis.brokerage.exception;

/**
 * Exception thrown when an order cannot be executed due to invalid state.
 */
public class OrderExecutionException extends RuntimeException {

    public OrderExecutionException(String message) {
        super(message);
    }

    public OrderExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

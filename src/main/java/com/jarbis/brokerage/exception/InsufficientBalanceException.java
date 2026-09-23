package com.jarbis.brokerage.exception;

/**
 * Exception thrown when attempting to withdraw more than the available balance.
 */
public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(String message) {
        super(message);
    }

    public InsufficientBalanceException(String message, Throwable cause) {
        super(message, cause);
    }
}

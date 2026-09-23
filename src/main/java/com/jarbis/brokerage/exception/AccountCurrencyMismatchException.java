package com.jarbis.brokerage.exception;

/**
 * Exception thrown when an operation requires matching account currencies.
 */
public class AccountCurrencyMismatchException extends RuntimeException {

    public AccountCurrencyMismatchException(String message) {
        super(message);
    }

    public AccountCurrencyMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}

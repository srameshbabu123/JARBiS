package com.jarbis.brokerage.exception;

/**
 * Exception thrown when an account cannot be closed because it still violates
 * one or more closure prerequisites.
 */
public class AccountClosureException extends RuntimeException {

    public AccountClosureException(String message) {
        super(message);
    }

    public AccountClosureException(String message, Throwable cause) {
        super(message, cause);
    }
}

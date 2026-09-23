package com.jarbis.brokerage.exception;

/**
 * Exception thrown when a transaction is not found in the database.
 */
public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(String message) {
        super(message);
    }

    public TransactionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

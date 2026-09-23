package com.jarbis.brokerage.exception;

/**
 * Exception thrown when a transaction has no participant orders to execute.
 */
public class EmptyTransactionException extends RuntimeException {

    public EmptyTransactionException(String message) {
        super(message);
    }

    public EmptyTransactionException(String message, Throwable cause) {
        super(message, cause);
    }
}

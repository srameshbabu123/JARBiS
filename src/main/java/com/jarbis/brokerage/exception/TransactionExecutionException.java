package com.jarbis.brokerage.exception;

/**
 * Exception thrown when a transaction execution fails during order processing.
 */
public class TransactionExecutionException extends RuntimeException {

    public TransactionExecutionException(String message) {
        super(message);
    }

    public TransactionExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

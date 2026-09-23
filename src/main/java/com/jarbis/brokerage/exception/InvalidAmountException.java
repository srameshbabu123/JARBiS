package com.jarbis.brokerage.exception;

/**
 * Exception thrown when a numeric business input is invalid.
 */
public class InvalidAmountException extends RuntimeException {

    public InvalidAmountException(String message) {
        super(message);
    }

    public InvalidAmountException(String message, Throwable cause) {
        super(message, cause);
    }
}

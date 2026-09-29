package com.jarbis.brokerage.exception;

/**
 * Exception thrown when a holding is not found.
 */
public class HoldingNotFoundException extends RuntimeException {

	public HoldingNotFoundException(String message) {
		super(message);
	}

	public HoldingNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}
}

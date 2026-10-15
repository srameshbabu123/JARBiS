package com.jarbis.brokerage.exception;

/**
 * Exception thrown when an asset cannot be found in the database.
 */
public class AssetNotFoundException extends RuntimeException {

	public AssetNotFoundException(String message) {
		super(message);
	}
}

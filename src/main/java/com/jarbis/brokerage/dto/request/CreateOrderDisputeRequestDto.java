package com.jarbis.brokerage.dto.request;

public class CreateOrderDisputeRequestDto {

	private String reason;
	private String details;

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public String getDetails() {
		return details;
	}

	public void setDetails(String details) {
		this.details = details;
	}
}
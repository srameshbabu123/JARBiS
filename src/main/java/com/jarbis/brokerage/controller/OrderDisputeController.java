package com.jarbis.brokerage.controller;

import com.jarbis.brokerage.dto.request.CreateOrderDisputeRequestDto;
import com.jarbis.brokerage.dto.response.OrderDisputeResponseDto;
import com.jarbis.brokerage.service.OrderDisputeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/orders/{orderId}/disputes")
public class OrderDisputeController {

	private final OrderDisputeService disputeService;

	public OrderDisputeController(OrderDisputeService disputeService) {
		this.disputeService = disputeService;
	}

	@GetMapping
	@org.springframework.security.access.prepost.PreAuthorize("@clientAccess.ownsOrder(authentication.principal, #orderId)")
	public List<OrderDisputeResponseDto> listByOrder(@PathVariable Long orderId) {
		return disputeService.byOrder(orderId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@org.springframework.security.access.prepost.PreAuthorize("@clientAccess.ownsOrder(authentication.principal, #orderId)")
	public OrderDisputeResponseDto submit(
			@PathVariable Long orderId,
			@RequestBody CreateOrderDisputeRequestDto body) {
		return disputeService.submit(orderId, body);
	}
}
package com.jarbis.brokerage.controller;

import com.jarbis.brokerage.dto.request.CreateOrderRequestDto;
import com.jarbis.brokerage.dto.request.UpdateOrderStatusRequestDto;
import com.jarbis.brokerage.entity.Account;
import com.jarbis.brokerage.entity.Asset;
import com.jarbis.brokerage.entity.Order;
import com.jarbis.brokerage.enums.OrderStatus;
import com.jarbis.brokerage.service.AccountService;
import com.jarbis.brokerage.service.AssetService;
import com.jarbis.brokerage.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {
	private final OrderService orderService;
	private final AccountService accountService;
	private final AssetService assetService;

	public OrderController(OrderService orderService, AccountService accountService, AssetService assetService) {
		this.orderService = orderService;
		this.accountService = accountService;
		this.assetService = assetService;
	}

	/**
	 * Create a new order
	 */
	@PostMapping
	public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequestDto requestDto) {
		Account account = accountService.getAccountById(requestDto.getAccountId());
		Asset asset = assetService.getAssetById(requestDto.getAssetId());

		Order order = orderService.createPendingOrder(asset, account, requestDto.getSide(), requestDto.getQuantity(),
				requestDto.getPrice());

		return ResponseEntity.status(HttpStatus.CREATED).body(order);
	}

	/**
	 * Get an order by ID
	 */
	@GetMapping("/{orderId}")
	public ResponseEntity<Order> getOrderById(@PathVariable Long orderId) {
		Order order = orderService.getOrderById(orderId);
		return ResponseEntity.ok(order);
	}

	/**
	 * Get all orders
	 */
	@GetMapping
	public ResponseEntity<List<Order>> getAllOrders() {
		List<Order> orders = orderService.getAllOrders();
		return ResponseEntity.ok(orders);
	}

	/**
	 * Get all orders for a specific account
	 */
	@GetMapping("/account/{accountId}")
	public ResponseEntity<List<Order>> getOrdersByAccount(@PathVariable Long accountId) {
		List<Order> orders = orderService.getOrdersByAccount(accountId);
		return ResponseEntity.ok(orders);
	}

	/**
	 * Get all orders for a specific user
	 */
	@GetMapping("/user/{userId}")
	public ResponseEntity<List<Order>> getOrdersByUser(@PathVariable Long userId) {
		List<Order> orders = orderService.getOrdersByUser(userId);
		return ResponseEntity.ok(orders);
	}

	/**
	 * Get all orders by status
	 */
	@GetMapping("/status/{status}")
	public ResponseEntity<List<Order>> getOrdersByStatus(@PathVariable OrderStatus status) {
		List<Order> orders = orderService.getOrdersByStatus(status);
		return ResponseEntity.ok(orders);
	}

	/**
	 * Get pending orders
	 */
	@GetMapping("/status/pending")
	public ResponseEntity<List<Order>> getPendingOrders() {
		List<Order> orders = orderService.getPendingOrders();
		return ResponseEntity.ok(orders);
	}

	/**
	 * Get completed orders
	 */
	@GetMapping("/status/completed")
	public ResponseEntity<List<Order>> getCompletedOrders() {
		List<Order> orders = orderService.getCompletedOrders();
		return ResponseEntity.ok(orders);
	}

	/**
	 * Get cancelled orders
	 */
	@GetMapping("/status/cancelled")
	public ResponseEntity<List<Order>> getCancelledOrders() {
		List<Order> orders = orderService.getCancelledOrders();
		return ResponseEntity.ok(orders);
	}

	/**
	 * Update order status
	 */
	@PutMapping("/{orderId}/status")
	public ResponseEntity<Order> updateOrderStatus(@PathVariable Long orderId,
			@RequestBody UpdateOrderStatusRequestDto requestDto) {
		Order order = orderService.updateOrderStatus(orderId, requestDto.getStatus());
		return ResponseEntity.ok(order);
	}

	/**
	 * Complete an order
	 */
	@PutMapping("/{orderId}/complete")
	public ResponseEntity<Order> completeOrder(@PathVariable Long orderId) {
		Order order = orderService.completeOrder(orderId);
		return ResponseEntity.ok(order);
	}

	/**
	 * Cancel an order
	 */
	@PutMapping("/{orderId}/cancel")
	public ResponseEntity<Order> cancelOrder(@PathVariable Long orderId) {
		Order order = orderService.cancelOrder(orderId);
		return ResponseEntity.ok(order);
	}

	/**
	 * Execute an order
	 */
	@PostMapping("/{orderId}/execute")
	public ResponseEntity<Void> executeOrder(@PathVariable Long orderId) {
		orderService.executeOrder(orderId);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Delete an order
	 */
	@DeleteMapping("/{orderId}")
	public ResponseEntity<Void> deleteOrder(@PathVariable Long orderId) {
		orderService.deleteOrder(orderId);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Get orders by user and status
	 */
	@GetMapping("/user/{userId}/status/{status}")
	public ResponseEntity<List<Order>> getOrdersByUserAndStatus(@PathVariable Long userId,
			@PathVariable OrderStatus status) {
		List<Order> orders = orderService.getOrdersByUserAndStatus(userId, status);
		return ResponseEntity.ok(orders);
	}

	/**
	 * Get orders by asset
	 */
	@GetMapping("/asset/{assetId}")
	public ResponseEntity<List<Order>> getOrdersByAsset(@PathVariable Long assetId) {
		List<Order> orders = orderService.getOrdersByAsset(assetId);
		return ResponseEntity.ok(orders);
	}

	/**
	 * Get orders by transaction
	 */
	@GetMapping("/transaction/{transactionId}")
	public ResponseEntity<List<Order>> getOrdersByTransaction(@PathVariable Long transactionId) {
		List<Order> orders = orderService.getOrdersByTransaction(transactionId);
		return ResponseEntity.ok(orders);
	}
}

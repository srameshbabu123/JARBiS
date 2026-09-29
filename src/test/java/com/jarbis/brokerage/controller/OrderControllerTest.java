package com.jarbis.brokerage.controller;

import com.jarbis.brokerage.dto.request.CreateOrderRequestDto;
import com.jarbis.brokerage.dto.request.UpdateOrderStatusRequestDto;
import com.jarbis.brokerage.entity.Order;
import com.jarbis.brokerage.enums.OrderSide;
import com.jarbis.brokerage.enums.OrderStatus;
import com.jarbis.brokerage.exception.OrderNotFoundException;
import com.jarbis.brokerage.service.AccountService;
import com.jarbis.brokerage.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for OrderController following TDD principles. Tests cover CRUD
 * operations, order status transitions, and filtering.
 */
class OrderControllerTest {

	private OrderService orderService;
	private AccountService accountService;
	private OrderController orderController;

	@BeforeEach
	void setUp() {
		orderService = mock(OrderService.class);
		accountService = mock(AccountService.class);
		orderController = new OrderController(orderService, accountService);
	}

	@Test
	void createOrderDelegatesToServiceAndReturnsCreatedOrder() {
		CreateOrderRequestDto requestDto = new CreateOrderRequestDto(1L, 1L, OrderSide.BUY, 100.0, 50.0);
		Order expectedOrder = mock(Order.class);
		when(expectedOrder.getStatus()).thenReturn(OrderStatus.PENDING);

		when(orderService.createPendingOrder(null, null, OrderSide.BUY, 100.0, 50.0)).thenReturn(expectedOrder);

		ResponseEntity<Order> response = orderController.createOrder(requestDto);

		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(OrderStatus.PENDING, response.getBody().getStatus());
	}

	@Test
	void getOrderByIdReturnsOrderFromService() {
		Order expectedOrder = mock(Order.class);
		when(orderService.getOrderById(1L)).thenReturn(expectedOrder);

		ResponseEntity<Order> response = orderController.getOrderById(1L);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		verify(orderService).getOrderById(1L);
	}

	@Test
	void getAllOrdersReturnsListFromService() {
		Order order1 = mock(Order.class);
		Order order2 = mock(Order.class);
		List<Order> expectedOrders = Arrays.asList(order1, order2);

		when(orderService.getAllOrders()).thenReturn(expectedOrders);

		ResponseEntity<List<Order>> response = orderController.getAllOrders();

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(2, response.getBody().size());
		verify(orderService).getAllOrders();
	}

	@Test
	void updateOrderStatusDelegatesToServiceAndReturnsUpdatedOrder() {
		UpdateOrderStatusRequestDto requestDto = new UpdateOrderStatusRequestDto(OrderStatus.COMPLETED);
		Order updatedOrder = mock(Order.class);
		when(updatedOrder.getStatus()).thenReturn(OrderStatus.COMPLETED);

		when(orderService.updateOrderStatus(1L, OrderStatus.COMPLETED)).thenReturn(updatedOrder);

		ResponseEntity<Order> response = orderController.updateOrderStatus(1L, requestDto);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(OrderStatus.COMPLETED, response.getBody().getStatus());
		verify(orderService).updateOrderStatus(1L, OrderStatus.COMPLETED);
	}

	@Test
	void completeOrderDelegatesToServiceAndReturnsCompletedOrder() {
		Order completedOrder = mock(Order.class);
		when(completedOrder.getStatus()).thenReturn(OrderStatus.COMPLETED);

		when(orderService.completeOrder(1L)).thenReturn(completedOrder);

		ResponseEntity<Order> response = orderController.completeOrder(1L);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(OrderStatus.COMPLETED, response.getBody().getStatus());
		verify(orderService).completeOrder(1L);
	}

	@Test
	void cancelOrderDelegatesToServiceAndReturnsCancelledOrder() {
		Order cancelledOrder = mock(Order.class);
		when(cancelledOrder.getStatus()).thenReturn(OrderStatus.CANCELLED);

		when(orderService.cancelOrder(1L)).thenReturn(cancelledOrder);

		ResponseEntity<Order> response = orderController.cancelOrder(1L);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(OrderStatus.CANCELLED, response.getBody().getStatus());
		verify(orderService).cancelOrder(1L);
	}

	@Test
	void executeOrderDelegatesToService() {
		orderController.executeOrder(1L);
		verify(orderService).executeOrder(1L);
	}

	@Test
	void deleteOrderDelegatesToService() {
		ResponseEntity<Void> response = orderController.deleteOrder(1L);

		assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
		verify(orderService).deleteOrder(1L);
	}

	@Nested
	class CreateOrderTests {

		@Test
		void testCreateBuyOrder() {
			CreateOrderRequestDto requestDto = new CreateOrderRequestDto(1L, 1L, OrderSide.BUY, 50.0, 100.0);
			Order order = mock(Order.class);
			when(order.getSide()).thenReturn(OrderSide.BUY);

			when(orderService.createPendingOrder(null, null, OrderSide.BUY, 50.0, 100.0)).thenReturn(order);

			ResponseEntity<Order> response = orderController.createOrder(requestDto);

			assertEquals(HttpStatus.CREATED, response.getStatusCode());
			assertNotNull(response.getBody());
			assertEquals(OrderSide.BUY, response.getBody().getSide());
		}

		@Test
		void testCreateSellOrder() {
			CreateOrderRequestDto requestDto = new CreateOrderRequestDto(1L, 1L, OrderSide.SELL, 30.0, 150.0);
			Order order = mock(Order.class);
			when(order.getSide()).thenReturn(OrderSide.SELL);

			when(orderService.createPendingOrder(null, null, OrderSide.SELL, 30.0, 150.0)).thenReturn(order);

			ResponseEntity<Order> response = orderController.createOrder(requestDto);

			assertEquals(HttpStatus.CREATED, response.getStatusCode());
			assertNotNull(response.getBody());
			assertEquals(OrderSide.SELL, response.getBody().getSide());
		}

		@Test
		void testCreateOrderWithLargeQuantity() {
			CreateOrderRequestDto requestDto = new CreateOrderRequestDto(1L, 1L, OrderSide.BUY, 10000.0, 50.0);
			Order order = mock(Order.class);
			when(order.getQuantity()).thenReturn(10000.0);

			when(orderService.createPendingOrder(null, null, OrderSide.BUY, 10000.0, 50.0)).thenReturn(order);

			ResponseEntity<Order> response = orderController.createOrder(requestDto);

			assertEquals(HttpStatus.CREATED, response.getStatusCode());
			assertEquals(10000.0, response.getBody().getQuantity());
		}
	}

	@Nested
	class GetOrderTests {

		@Test
		void testGetOrderByIdWithValidId() {
			Order expectedOrder = mock(Order.class);
			when(orderService.getOrderById(1L)).thenReturn(expectedOrder);

			ResponseEntity<Order> response = orderController.getOrderById(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertNotNull(response.getBody());
		}

		@Test
		void testGetOrderByIdThrowsExceptionForInvalidId() {
			when(orderService.getOrderById(999L)).thenThrow(new OrderNotFoundException("Order not found"));

			assertThrows(OrderNotFoundException.class, () -> orderController.getOrderById(999L));
		}

		@Test
		void testGetOrdersByAccount() {
			Order order1 = mock(Order.class);
			Order order2 = mock(Order.class);
			List<Order> expectedOrders = Arrays.asList(order1, order2);

			when(orderService.getOrdersByAccount(1L)).thenReturn(expectedOrders);

			ResponseEntity<List<Order>> response = orderController.getOrdersByAccount(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(2, response.getBody().size());
		}

		@Test
		void testGetOrdersByUser() {
			Order order1 = mock(Order.class);
			Order order2 = mock(Order.class);
			List<Order> expectedOrders = Arrays.asList(order1, order2);

			when(orderService.getOrdersByUser(1L)).thenReturn(expectedOrders);

			ResponseEntity<List<Order>> response = orderController.getOrdersByUser(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(2, response.getBody().size());
		}

		@Test
		void testGetOrdersByStatus() {
			Order order = mock(Order.class);
			when(order.getStatus()).thenReturn(OrderStatus.PENDING);
			List<Order> expectedOrders = Arrays.asList(order);

			when(orderService.getOrdersByStatus(OrderStatus.PENDING)).thenReturn(expectedOrders);

			ResponseEntity<List<Order>> response = orderController.getOrdersByStatus(OrderStatus.PENDING);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(1, response.getBody().size());
			assertEquals(OrderStatus.PENDING, response.getBody().get(0).getStatus());
		}

		@Test
		void testGetPendingOrders() {
			Order order = mock(Order.class);
			when(order.getStatus()).thenReturn(OrderStatus.PENDING);
			List<Order> expectedOrders = Arrays.asList(order);

			when(orderService.getPendingOrders()).thenReturn(expectedOrders);

			ResponseEntity<List<Order>> response = orderController.getPendingOrders();

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertTrue(response.getBody().stream().allMatch(o -> o.getStatus() == OrderStatus.PENDING));
		}

		@Test
		void testGetCompletedOrders() {
			Order order = mock(Order.class);
			when(order.getStatus()).thenReturn(OrderStatus.COMPLETED);
			List<Order> expectedOrders = Arrays.asList(order);

			when(orderService.getCompletedOrders()).thenReturn(expectedOrders);

			ResponseEntity<List<Order>> response = orderController.getCompletedOrders();

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertTrue(response.getBody().stream().allMatch(o -> o.getStatus() == OrderStatus.COMPLETED));
		}

		@Test
		void testGetCancelledOrders() {
			Order order = mock(Order.class);
			when(order.getStatus()).thenReturn(OrderStatus.CANCELLED);
			List<Order> expectedOrders = Arrays.asList(order);

			when(orderService.getCancelledOrders()).thenReturn(expectedOrders);

			ResponseEntity<List<Order>> response = orderController.getCancelledOrders();

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertTrue(response.getBody().stream().allMatch(o -> o.getStatus() == OrderStatus.CANCELLED));
		}
	}

	@Nested
	class UpdateOrderTests {

		@Test
		void testUpdateOrderToPending() {
			UpdateOrderStatusRequestDto requestDto = new UpdateOrderStatusRequestDto(OrderStatus.PENDING);
			Order updatedOrder = mock(Order.class);
			when(updatedOrder.getStatus()).thenReturn(OrderStatus.PENDING);

			when(orderService.updateOrderStatus(1L, OrderStatus.PENDING)).thenReturn(updatedOrder);

			ResponseEntity<Order> response = orderController.updateOrderStatus(1L, requestDto);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(OrderStatus.PENDING, response.getBody().getStatus());
		}

		@Test
		void testUpdateOrderToCompleted() {
			UpdateOrderStatusRequestDto requestDto = new UpdateOrderStatusRequestDto(OrderStatus.COMPLETED);
			Order updatedOrder = mock(Order.class);
			when(updatedOrder.getStatus()).thenReturn(OrderStatus.COMPLETED);

			when(orderService.updateOrderStatus(1L, OrderStatus.COMPLETED)).thenReturn(updatedOrder);

			ResponseEntity<Order> response = orderController.updateOrderStatus(1L, requestDto);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(OrderStatus.COMPLETED, response.getBody().getStatus());
		}

		@Test
		void testUpdateOrderToCancelled() {
			UpdateOrderStatusRequestDto requestDto = new UpdateOrderStatusRequestDto(OrderStatus.CANCELLED);
			Order updatedOrder = mock(Order.class);
			when(updatedOrder.getStatus()).thenReturn(OrderStatus.CANCELLED);

			when(orderService.updateOrderStatus(1L, OrderStatus.CANCELLED)).thenReturn(updatedOrder);

			ResponseEntity<Order> response = orderController.updateOrderStatus(1L, requestDto);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(OrderStatus.CANCELLED, response.getBody().getStatus());
		}

		@Test
		void testUpdateNonExistentOrderThrowsException() {
			UpdateOrderStatusRequestDto requestDto = new UpdateOrderStatusRequestDto(OrderStatus.COMPLETED);

			when(orderService.updateOrderStatus(999L, OrderStatus.COMPLETED))
					.thenThrow(new OrderNotFoundException("Order not found"));

			assertThrows(OrderNotFoundException.class, () -> orderController.updateOrderStatus(999L, requestDto));
		}
	}

	@Nested
	class DeleteOrderTests {

		@Test
		void testDeleteExistingOrder() {
			ResponseEntity<Void> response = orderController.deleteOrder(1L);

			assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
			verify(orderService).deleteOrder(1L);
		}

		@Test
		void testDeleteNonExistentOrderThrowsException() {
			doThrow(new OrderNotFoundException("Order not found")).when(orderService).deleteOrder(999L);

			assertThrows(OrderNotFoundException.class, () -> orderController.deleteOrder(999L));
		}
	}

	@Nested
	class FilteringTests {

		@Test
		void testGetOrdersByUserAndStatus() {
			Order order = mock(Order.class);
			when(order.getStatus()).thenReturn(OrderStatus.PENDING);
			List<Order> expectedOrders = Arrays.asList(order);

			when(orderService.getOrdersByUserAndStatus(1L, OrderStatus.PENDING)).thenReturn(expectedOrders);

			ResponseEntity<List<Order>> response = orderController.getOrdersByUserAndStatus(1L, OrderStatus.PENDING);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(1, response.getBody().size());
			assertEquals(OrderStatus.PENDING, response.getBody().get(0).getStatus());
		}

		@Test
		void testGetOrdersByAsset() {
			Order order = mock(Order.class);
			List<Order> expectedOrders = Arrays.asList(order);

			when(orderService.getOrdersByAsset(1L)).thenReturn(expectedOrders);

			ResponseEntity<List<Order>> response = orderController.getOrdersByAsset(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(1, response.getBody().size());
		}

		@Test
		void testGetOrdersByTransaction() {
			Order order = mock(Order.class);
			when(order.getStatus()).thenReturn(OrderStatus.COMPLETED);
			List<Order> expectedOrders = Arrays.asList(order);

			when(orderService.getOrdersByTransaction(1L)).thenReturn(expectedOrders);

			ResponseEntity<List<Order>> response = orderController.getOrdersByTransaction(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(1, response.getBody().size());
		}
	}
}

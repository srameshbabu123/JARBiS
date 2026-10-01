package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.Account;
import com.jarbis.brokerage.entity.Asset;
import com.jarbis.brokerage.entity.Order;
import com.jarbis.brokerage.entity.Transaction;
import com.jarbis.brokerage.enums.OrderSide;
import com.jarbis.brokerage.enums.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettlementEngineServiceTest {

	private OrderService orderService;
	private TransactionService transactionService;
	private AccountService accountService;
	private SettlementEngineService settlementEngineService;

	@BeforeEach
	void setUp() {
		orderService = mock(OrderService.class);
		transactionService = mock(TransactionService.class);
		accountService = mock(AccountService.class);
		settlementEngineService = new SettlementEngineService(orderService, transactionService, accountService,
				new OrderBookService());

		when(orderService.saveOrder(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void processSubmittedOrderMatchesCrossedOrdersAndCompletesTrade() {
		Asset asset = assetWithId(7L);
		Account buyAccount = accountWithId(11L);
		Account sellAccount = accountWithId(12L);
		Order buyOrder = orderWithId(1L, asset, buyAccount, OrderSide.BUY, 5.0, 101.0);
		Order sellOrder = orderWithId(2L, asset, sellAccount, OrderSide.SELL, 5.0, 99.0);
		Transaction transaction = transactionWithId(200L);

		when(orderService.getOrderById(2L)).thenReturn(sellOrder);
		when(orderService.getOpenOrdersByAsset(7L)).thenReturn(List.of(buyOrder, sellOrder));
		when(transactionService.createPendingTransaction(asset, 5.0, 101.0, buyOrder, sellOrder)).thenReturn(transaction);

		settlementEngineService.processSubmittedOrder(2L);

		assertEquals(OrderStatus.COMPLETED, buyOrder.getStatus());
		assertEquals(OrderStatus.COMPLETED, sellOrder.getStatus());
		assertEquals(0.0, buyOrder.getRemainingQuantity());
		assertEquals(0.0, sellOrder.getRemainingQuantity());

		verify(accountService).buyAsset(11L, asset, 5.0, 101.0);
		verify(accountService).sellAsset(12L, asset, 5.0, 101.0);
		verify(orderService).saveOrder(buyOrder);
		verify(orderService).saveOrder(sellOrder);
		verify(transactionService).createPendingTransaction(asset, 5.0, 101.0, buyOrder, sellOrder);
		verify(transactionService).completeTransaction(200L);
	}

	@Test
	void processSubmittedOrderPartiallyFillsLargerOrderAndKeepsItOpen() {
		Asset asset = assetWithId(8L);
		Account buyAccount = accountWithId(21L);
		Account sellAccount = accountWithId(22L);
		Order buyOrder = orderWithId(1L, asset, buyAccount, OrderSide.BUY, 10.0, 110.0);
		Order sellOrder = orderWithId(2L, asset, sellAccount, OrderSide.SELL, 4.0, 100.0);
		Transaction transaction = transactionWithId(201L);

		when(orderService.getOrderById(2L)).thenReturn(sellOrder);
		when(orderService.getOpenOrdersByAsset(8L)).thenReturn(List.of(buyOrder, sellOrder));
		when(transactionService.createPendingTransaction(asset, 4.0, 110.0, buyOrder, sellOrder)).thenReturn(transaction);

		settlementEngineService.processSubmittedOrder(2L);

		assertEquals(OrderStatus.PARTIALLY_FILLED, buyOrder.getStatus());
		assertEquals(6.0, buyOrder.getRemainingQuantity());
		assertEquals(OrderStatus.COMPLETED, sellOrder.getStatus());
		assertEquals(0.0, sellOrder.getRemainingQuantity());

		verify(accountService).buyAsset(21L, asset, 4.0, 110.0);
		verify(accountService).sellAsset(22L, asset, 4.0, 110.0);
		verify(transactionService).completeTransaction(201L);
	}

	@Test
	void processSubmittedOrderExecutesAtRestingSellPriceWhenBuyOrderIsIncoming() {
		Asset asset = assetWithId(11L);
		Account buyAccount = accountWithId(51L);
		Account sellAccount = accountWithId(52L);
		Order buyOrder = orderWithId(2L, asset, buyAccount, OrderSide.BUY, 3.0, 110.0);
		Order sellOrder = orderWithId(1L, asset, sellAccount, OrderSide.SELL, 3.0, 101.0);
		Transaction transaction = transactionWithId(203L);

		when(orderService.getOrderById(2L)).thenReturn(buyOrder);
		when(orderService.getOpenOrdersByAsset(11L)).thenReturn(List.of(sellOrder));
		when(transactionService.createPendingTransaction(asset, 3.0, 101.0, buyOrder, sellOrder)).thenReturn(transaction);

		settlementEngineService.processSubmittedOrder(2L);

		verify(accountService).buyAsset(51L, asset, 3.0, 101.0);
		verify(accountService).sellAsset(52L, asset, 3.0, 101.0);
		verify(transactionService).createPendingTransaction(asset, 3.0, 101.0, buyOrder, sellOrder);
		verify(transactionService).completeTransaction(203L);
	}

	@Test
	void processSubmittedOrderLeavesBookUnchangedWhenPricesDoNotCross() {
		Asset asset = assetWithId(9L);
		Account buyAccount = accountWithId(31L);
		Account sellAccount = accountWithId(32L);
		Order buyOrder = orderWithId(1L, asset, buyAccount, OrderSide.BUY, 5.0, 95.0);
		Order sellOrder = orderWithId(2L, asset, sellAccount, OrderSide.SELL, 5.0, 100.0);

		when(orderService.getOrderById(1L)).thenReturn(buyOrder);
		when(orderService.getOpenOrdersByAsset(9L)).thenReturn(List.of(buyOrder, sellOrder));

		settlementEngineService.processSubmittedOrder(1L);

		assertEquals(OrderStatus.PENDING, buyOrder.getStatus());
		assertEquals(OrderStatus.PENDING, sellOrder.getStatus());
		assertEquals(5.0, buyOrder.getRemainingQuantity());
		assertEquals(5.0, sellOrder.getRemainingQuantity());

		verify(transactionService, never()).createPendingTransaction(any(Asset.class), anyDouble(), anyDouble(),
				any(Order.class), any(Order.class));
		verify(accountService, never()).buyAsset(anyLong(), any(Asset.class), anyDouble(), anyDouble());
		verify(accountService, never()).sellAsset(anyLong(), any(Asset.class), anyDouble(), anyDouble());
	}

	@Test
	void processSubmittedOrderReusesExistingLiveBookInsteadOfReloadingOpenOrdersEachTime() {
		Asset asset = assetWithId(10L);
		Account buyAccount = accountWithId(41L);
		Account sellAccount = accountWithId(42L);
		Order buyOrder = orderWithId(1L, asset, buyAccount, OrderSide.BUY, 5.0, 105.0);
		Order sellOrder = orderWithId(2L, asset, sellAccount, OrderSide.SELL, 5.0, 100.0);
		Transaction transaction = transactionWithId(202L);

		when(orderService.getOrderById(1L)).thenReturn(buyOrder);
		when(orderService.getOrderById(2L)).thenReturn(sellOrder);
		when(orderService.getOpenOrdersByAsset(10L)).thenReturn(List.of(buyOrder));
		when(transactionService.createPendingTransaction(asset, 5.0, 105.0, buyOrder, sellOrder)).thenReturn(transaction);

		settlementEngineService.processSubmittedOrder(1L);
		settlementEngineService.processSubmittedOrder(2L);

		verify(orderService, times(1)).getOpenOrdersByAsset(10L);
		verify(transactionService).createPendingTransaction(asset, 5.0, 105.0, buyOrder, sellOrder);
		verify(transactionService).completeTransaction(202L);
	}

	private Order orderWithId(Long id, Asset asset, Account account, OrderSide side, Double quantity, Double price) {
		Order order = new Order(asset, account, OrderStatus.PENDING);
		ReflectionTestUtils.setField(order, "id", id);
		order.setSide(side);
		order.setQuantity(quantity);
		order.setRemainingQuantity(quantity);
		order.setPrice(price);
		order.setSubmittedAt(LocalDateTime.of(2026, 1, 1, 0, 0).plusSeconds(id));
		return order;
	}

	private Transaction transactionWithId(Long id) {
		Transaction transaction = new Transaction();
		ReflectionTestUtils.setField(transaction, "id", id);
		return transaction;
	}

	private Account accountWithId(Long id) {
		Account account = mock(Account.class);
		when(account.getId()).thenReturn(id);
		return account;
	}

	private Asset assetWithId(Long id) {
		Asset asset = mock(Asset.class);
		when(asset.getId()).thenReturn(id);
		return asset;
	}
}

package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.Account;
import com.jarbis.brokerage.entity.Asset;
import com.jarbis.brokerage.entity.Order;
import com.jarbis.brokerage.enums.OrderSide;
import com.jarbis.brokerage.enums.OrderStatus;
import com.jarbis.brokerage.matching.AssetOrderBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderBookServiceTest {

	private OrderBookService orderBookService;

	@BeforeEach
	void setUp() {
		orderBookService = new OrderBookService();
	}

	@Test
	void getOrCreateBookBuildsBookOnceAndCachesIt() {
		Asset asset = assetWithId(1L);
		Account buyAccount = accountWithId(11L);
		Account sellAccount = accountWithId(12L);
		Order bid = orderWithId(1L, asset, buyAccount, OrderSide.BUY, OrderStatus.PENDING, 5.0, 101.0,
				LocalDateTime.of(2026, 1, 1, 9, 0, 0));
		Order ask = orderWithId(2L, asset, sellAccount, OrderSide.SELL, OrderStatus.PENDING, 3.0, 102.0,
				LocalDateTime.of(2026, 1, 1, 9, 1, 0));
		AtomicInteger supplierInvocations = new AtomicInteger();

		AssetOrderBook firstBook = orderBookService.getOrCreateBook(1L, () -> {
			supplierInvocations.incrementAndGet();
			return List.of(bid, ask);
		});
		AssetOrderBook secondBook = orderBookService.getOrCreateBook(1L, () -> {
			supplierInvocations.incrementAndGet();
			return List.of();
		});

		assertSame(firstBook, secondBook);
		assertEquals(1, supplierInvocations.get());
		assertSame(bid, firstBook.peekBestBid());
		assertSame(ask, firstBook.peekBestAsk());
	}

	@Test
	void getOrCreateBookPreservesTimePriorityWithinSamePriceLevel() {
		Asset asset = assetWithId(2L);
		Account olderAccount = accountWithId(21L);
		Account newerAccount = accountWithId(22L);
		Order olderBid = orderWithId(1L, asset, olderAccount, OrderSide.BUY, OrderStatus.PENDING, 4.0, 100.0,
				LocalDateTime.of(2026, 1, 1, 10, 0, 0));
		Order newerBid = orderWithId(2L, asset, newerAccount, OrderSide.BUY, OrderStatus.PENDING, 4.0, 100.0,
				LocalDateTime.of(2026, 1, 1, 10, 1, 0));

		AssetOrderBook book = orderBookService.getOrCreateBook(2L, () -> List.of(newerBid, olderBid));

		assertSame(olderBid, book.peekBestBid());
	}

	@Test
	void upsertOrderAddsOpenOrderToExistingBook() {
		Asset asset = assetWithId(3L);
		Account account = accountWithId(31L);
		Order ask = orderWithId(1L, asset, account, OrderSide.SELL, OrderStatus.PENDING, 2.0, 105.0,
				LocalDateTime.of(2026, 1, 1, 11, 0, 0));

		AssetOrderBook book = orderBookService.getOrCreateBook(3L, List::of);
		orderBookService.upsertOrder(ask);

		assertSame(book, orderBookService.getBook(3L));
		assertTrue(book.contains(1L));
		assertSame(ask, book.peekBestAsk());
	}

	@Test
	void upsertOrderRemovesCompletedOrderAndEvictsEmptyBook() {
		Asset asset = assetWithId(4L);
		Account account = accountWithId(41L);
		Order bid = orderWithId(1L, asset, account, OrderSide.BUY, OrderStatus.PENDING, 2.0, 99.0,
				LocalDateTime.of(2026, 1, 1, 12, 0, 0));

		orderBookService.getOrCreateBook(4L, () -> List.of(bid));
		bid.setStatus(OrderStatus.COMPLETED);
		bid.setRemainingQuantity(0.0);

		orderBookService.upsertOrder(bid);

		assertNull(orderBookService.getBook(4L));
	}

	@Test
	void removeOrderDeletesSpecificOrderButKeepsBookWhenOthersRemain() {
		Asset asset = assetWithId(5L);
		Account firstAccount = accountWithId(51L);
		Account secondAccount = accountWithId(52L);
		Order bestBid = orderWithId(1L, asset, firstAccount, OrderSide.BUY, OrderStatus.PENDING, 3.0, 101.0,
				LocalDateTime.of(2026, 1, 1, 13, 0, 0));
		Order nextBid = orderWithId(2L, asset, secondAccount, OrderSide.BUY, OrderStatus.PENDING, 3.0, 100.0,
				LocalDateTime.of(2026, 1, 1, 13, 1, 0));

		AssetOrderBook book = orderBookService.getOrCreateBook(5L, () -> List.of(bestBid, nextBid));
		orderBookService.removeOrder(5L, 1L);

		assertSame(book, orderBookService.getBook(5L));
		assertFalse(book.contains(1L));
		assertTrue(book.contains(2L));
		assertSame(nextBid, book.peekBestBid());
	}

	@Test
	void removeOrderEvictsBookWhenLastOrderIsRemoved() {
		Asset asset = assetWithId(6L);
		Account account = accountWithId(61L);
		Order onlyAsk = orderWithId(1L, asset, account, OrderSide.SELL, OrderStatus.PENDING, 1.0, 103.0,
				LocalDateTime.of(2026, 1, 1, 14, 0, 0));

		orderBookService.getOrCreateBook(6L, () -> List.of(onlyAsk));
		orderBookService.removeOrder(6L, 1L);

		assertNull(orderBookService.getBook(6L));
	}

	@Test
	void getOrCreateBookSkipsNonTrackableOrdersDuringHydration() {
		Asset asset = assetWithId(7L);
		Account openAccount = accountWithId(71L);
		Account closedAccount = accountWithId(72L);
		Order openBid = orderWithId(1L, asset, openAccount, OrderSide.BUY, OrderStatus.PENDING, 5.0, 98.0,
				LocalDateTime.of(2026, 1, 1, 15, 0, 0));
		Order completedAsk = orderWithId(2L, asset, closedAccount, OrderSide.SELL, OrderStatus.COMPLETED, 5.0, 97.0,
				LocalDateTime.of(2026, 1, 1, 15, 1, 0));
		completedAsk.setRemainingQuantity(0.0);

		AssetOrderBook book = orderBookService.getOrCreateBook(7L, () -> List.of(openBid, completedAsk));

		assertSame(openBid, book.peekBestBid());
		assertNull(book.peekBestAsk());
		assertFalse(book.hasMatch());
	}

	private Order orderWithId(Long id, Asset asset, Account account, OrderSide side, OrderStatus status,
			Double quantity, Double price, LocalDateTime submittedAt) {
		Order order = new Order(asset, account, status);
		ReflectionTestUtils.setField(order, "id", id);
		order.setSide(side);
		order.setQuantity(quantity);
		order.setRemainingQuantity(quantity);
		order.setPrice(price);
		order.setSubmittedAt(submittedAt);
		return order;
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

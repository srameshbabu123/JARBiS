package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.Order;
import com.jarbis.brokerage.matching.AssetOrderBook;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@Service
public class OrderBookService {

	private final ConcurrentMap<Long, AssetOrderBook> booksByAsset = new ConcurrentHashMap<>();

	public AssetOrderBook getOrCreateBook(Long assetId, Supplier<List<Order>> openOrdersSupplier) {
		return booksByAsset.computeIfAbsent(assetId, ignored -> buildBook(assetId, openOrdersSupplier.get()));
	}

	public void evict(Long assetId) {
		booksByAsset.remove(assetId);
	}

	public void removeOrder(Long assetId, Long orderId) {
		AssetOrderBook book = booksByAsset.get(assetId);
		if (book == null) {
			return;
		}

		book.lock();
		try {
			book.remove(orderId);
			if (book.isEmpty()) {
				booksByAsset.remove(assetId, book);
			}
		} finally {
			book.unlock();
		}
	}

	public void upsertOrder(Order order) {
		if (order == null || order.getAsset() == null || order.getAsset().getId() == null) {
			return;
		}

		AssetOrderBook book = booksByAsset.get(order.getAsset().getId());
		if (book == null) {
			return;
		}

		book.lock();
		try {
			book.upsert(order);
			if (book.isEmpty()) {
				booksByAsset.remove(order.getAsset().getId(), book);
			}
		} finally {
			book.unlock();
		}
	}

	public AssetOrderBook getBook(Long assetId) {
		return booksByAsset.get(assetId);
	}

	private AssetOrderBook buildBook(Long assetId, List<Order> openOrders) {
		AssetOrderBook book = new AssetOrderBook(assetId);
		openOrders.stream()
				.sorted(Comparator.comparing(Order::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo))
						.thenComparing(Order::getId, Comparator.nullsLast(Long::compareTo)))
				.forEach(book::add);
		return book;
	}
}

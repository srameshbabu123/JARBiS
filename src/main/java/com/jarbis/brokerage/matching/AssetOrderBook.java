package com.jarbis.brokerage.matching;

import com.jarbis.brokerage.entity.Order;
import com.jarbis.brokerage.enums.OrderSide;
import com.jarbis.brokerage.enums.OrderStatus;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Live in-memory order book with price-time priority for a single asset.
 */
public class AssetOrderBook {

	private final Long assetId;
	private final NavigableMap<Double, PriceLevel> bids = new TreeMap<>(Comparator.reverseOrder());
	private final NavigableMap<Double, PriceLevel> asks = new TreeMap<>();
	private final Map<Long, OrderPointer> orderIndex = new HashMap<>();
	private final ReentrantLock lock = new ReentrantLock();

	public AssetOrderBook(Long assetId) {
		this.assetId = assetId;
	}

	public Long getAssetId() {
		return assetId;
	}

	public void lock() {
		lock.lock();
	}

	public void unlock() {
		lock.unlock();
	}

	public boolean contains(Long orderId) {
		return orderIndex.containsKey(orderId);
	}

	public boolean isEmpty() {
		return bids.isEmpty() && asks.isEmpty();
	}

	public void add(Order order) {
		if (!isTrackable(order) || order.getId() == null) {
			return;
		}

		remove(order.getId());

		NavigableMap<Double, PriceLevel> sideBook = sideBook(order.getSide());
		PriceLevel level = sideBook.computeIfAbsent(order.getPrice(), ignored -> new PriceLevel());
		level.add(order);
		orderIndex.put(order.getId(), new OrderPointer(order.getSide(), order.getPrice()));
	}

	public void upsert(Order order) {
		if (!isTrackable(order)) {
			if (order != null && order.getId() != null) {
				remove(order.getId());
			}
			return;
		}

		add(order);
	}

	public Order remove(Long orderId) {
		if (orderId == null) {
			return null;
		}

		OrderPointer pointer = orderIndex.remove(orderId);
		if (pointer == null) {
			return null;
		}

		NavigableMap<Double, PriceLevel> sideBook = sideBook(pointer.side());
		PriceLevel level = sideBook.get(pointer.price());
		if (level == null) {
			return null;
		}

		Order removedOrder = level.remove(orderId);
		if (level.isEmpty()) {
			sideBook.remove(pointer.price());
		}
		return removedOrder;
	}

	public boolean hasMatch() {
		Order bestBid = peekBestBid();
		Order bestAsk = peekBestAsk();
		return bestBid != null && bestAsk != null && bestBid.getPrice() >= bestAsk.getPrice();
	}

	public Order peekBestBid() {
		return peekBest(bids);
	}

	public Order peekBestAsk() {
		return peekBest(asks);
	}

	private Order peekBest(NavigableMap<Double, PriceLevel> sideBook) {
		while (!sideBook.isEmpty()) {
			Map.Entry<Double, PriceLevel> bestLevelEntry = sideBook.firstEntry();
			PriceLevel bestLevel = bestLevelEntry.getValue();
			Order bestOrder = bestLevel.peekFirst();

			if (isTrackable(bestOrder)) {
				return bestOrder;
			}

			if (bestOrder != null) {
				orderIndex.remove(bestOrder.getId());
				bestLevel.remove(bestOrder.getId());
			}

			if (bestLevel.isEmpty()) {
				sideBook.pollFirstEntry();
			}
		}
		return null;
	}

	private NavigableMap<Double, PriceLevel> sideBook(OrderSide side) {
		return side == OrderSide.BUY ? bids : asks;
	}

	private boolean isTrackable(Order order) {
		return order != null && order.getRemainingQuantity() != null && order.getRemainingQuantity() > 0
				&& (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.PARTIALLY_FILLED);
	}

	private static final class PriceLevel {

		private final LinkedHashMap<Long, Order> orders = new LinkedHashMap<>();

		private void add(Order order) {
			orders.put(order.getId(), order);
		}

		private Order remove(Long orderId) {
			return orders.remove(orderId);
		}

		private Order peekFirst() {
			return orders.values().stream().findFirst().orElse(null);
		}

		private boolean isEmpty() {
			return orders.isEmpty();
		}
	}

	private record OrderPointer(OrderSide side, Double price) {
	}
}

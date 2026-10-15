package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.Order;
import com.jarbis.brokerage.entity.Transaction;
import com.jarbis.brokerage.enums.OrderStatus;
import com.jarbis.brokerage.exception.TransactionExecutionException;
import com.jarbis.brokerage.matching.AssetOrderBook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementEngineService {

	private final OrderService orderService;
	private final TransactionService transactionService;
	private final AccountService accountService;
	private final OrderBookService orderBookService;

	@Autowired
	public SettlementEngineService(OrderService orderService, TransactionService transactionService,
			AccountService accountService, OrderBookService orderBookService) {
		this.orderService = orderService;
		this.transactionService = transactionService;
		this.accountService = accountService;
		this.orderBookService = orderBookService;
	}

	@Transactional
	public void processSubmittedOrder(Long orderId) {
		Order submittedOrder = orderService.getOrderById(orderId);
		Long assetId = submittedOrder.getAsset().getId();
		AssetOrderBook orderBook = orderBookService.getOrCreateBook(assetId,
				() -> orderService.getOpenOrdersByAsset(assetId));

		orderBook.lock();
		try {
			orderBook.upsert(submittedOrder);
			settleBook(orderBook);
			if (orderBook.isEmpty()) {
				orderBookService.evict(assetId);
			}
		} catch (RuntimeException ex) {
			orderBookService.evict(assetId);
			throw ex;
		} finally {
			orderBook.unlock();
		}
	}

	private void settleBook(AssetOrderBook orderBook) {
		while (orderBook.hasMatch()) {
			Order bestBid = orderBook.peekBestBid();
			Order bestAsk = orderBook.peekBestAsk();

			double matchedQuantity = Math.min(bestBid.getRemainingQuantity(), bestAsk.getRemainingQuantity());
			double executionPrice = determineExecutionPrice(bestBid, bestAsk);

			Transaction transaction = transactionService.createPendingTransaction(bestBid.getAsset(), matchedQuantity,
					executionPrice, bestBid, bestAsk);

			try {
				accountService.buyAsset(bestBid.getAccount().getId(), bestBid.getAsset(), matchedQuantity, executionPrice);
				accountService.sellAsset(bestAsk.getAccount().getId(), bestAsk.getAsset(), matchedQuantity, executionPrice);

				applyFill(bestBid, matchedQuantity);
				applyFill(bestAsk, matchedQuantity);

				orderService.saveOrder(bestBid);
				orderService.saveOrder(bestAsk);

				if (bestBid.getStatus() == OrderStatus.COMPLETED) {
					orderBook.remove(bestBid.getId());
				}
				if (bestAsk.getStatus() == OrderStatus.COMPLETED) {
					orderBook.remove(bestAsk.getId());
				}

				transactionService.completeTransaction(transaction.getId());
			} catch (RuntimeException ex) {
				transactionService.failTransaction(transaction.getId());
				throw new TransactionExecutionException(
						"Settlement failed while matching orders " + bestBid.getId() + " and " + bestAsk.getId(), ex);
			}
		}
	}

	private double determineExecutionPrice(Order buyOrder, Order sellOrder) {
		return sellOrder.getSubmittedAt().isBefore(buyOrder.getSubmittedAt()) ? sellOrder.getPrice()
				: buyOrder.getPrice();
	}

	private void applyFill(Order order, double matchedQuantity) {
		double remainingQuantity = order.getRemainingQuantity() - matchedQuantity;
		if (remainingQuantity < 0) {
			remainingQuantity = 0; // reevaluate to avoid negative remaining quantity
		}
		order.setRemainingQuantity(remainingQuantity);

		if (remainingQuantity == 0) {
			order.setStatus(OrderStatus.COMPLETED);
			return;
		}

		order.setStatus(OrderStatus.PARTIALLY_FILLED);
	}
}

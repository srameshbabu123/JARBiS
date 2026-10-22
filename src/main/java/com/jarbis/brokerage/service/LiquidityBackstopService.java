package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.Account;
import com.jarbis.brokerage.entity.Asset;
import com.jarbis.brokerage.entity.Holding;
import com.jarbis.brokerage.entity.Order;
import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.enums.AccountType;
import com.jarbis.brokerage.enums.Currency;
import com.jarbis.brokerage.enums.OrderSide;
import com.jarbis.brokerage.matching.AssetOrderBook;
import com.jarbis.brokerage.repository.AccountRepository;
import com.jarbis.brokerage.repository.HoldingRepository;
import com.jarbis.brokerage.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LiquidityBackstopService {

	private static final String LIQUIDITY_EMAIL = "liquidity-bot@local.test";
	private static final String LIQUIDITY_NAME = "Local Liquidity Bot";
	private static final String LIQUIDITY_PASSWORD_HASH = "$2b$10$oYjqramDU/vVNANvKwm2N.VTbQCkVeHvlssLA2pam67S.ZShF9SlS";
	private static final double MIN_HOLDING_BUFFER = 10000.0;
	private final boolean enabled;

	private final UserRepository userRepository;
	private final AccountRepository accountRepository;
	private final HoldingRepository holdingRepository;
	private final OrderService orderService;

	public LiquidityBackstopService(UserRepository userRepository, AccountRepository accountRepository,
			HoldingRepository holdingRepository, OrderService orderService,
			@Value("${brokerage.settlement.liquidity-backstop.enabled:false}") boolean enabled) {
		this.userRepository = userRepository;
		this.accountRepository = accountRepository;
		this.holdingRepository = holdingRepository;
		this.orderService = orderService;
		this.enabled = enabled;
	}

	public void provideAskLiquidityIfNeeded(Order submittedOrder, AssetOrderBook orderBook) {
		if (!enabled) {
			return;
		}
		if (submittedOrder == null || orderBook == null) {
			return;
		}
		if (submittedOrder.getSide() != OrderSide.BUY) {
			return;
		}
		if (!orderService.isOrderExecutable(submittedOrder)) {
			return;
		}
		if (orderBook.hasMatch()) {
			return;
		}
		if (orderBook.peekBestAsk() != null) {
			return;
		}

		Order bestBid = orderBook.peekBestBid();
		if (bestBid == null || !submittedOrder.getId().equals(bestBid.getId())) {
			return;
		}

		Account liquidityAccount = ensureLiquidityAccount();
		double desiredQuantity = submittedOrder.getRemainingQuantity();
		ensureSellableHolding(liquidityAccount, submittedOrder.getAsset(), desiredQuantity, submittedOrder.getPrice());

		Order liquidityAsk = orderService.createPendingOrder(submittedOrder.getAsset(), liquidityAccount, OrderSide.SELL,
				desiredQuantity, submittedOrder.getPrice());
		orderBook.upsert(liquidityAsk);
	}

	private Account ensureLiquidityAccount() {
		User liquidityUser = userRepository.findByEmail(LIQUIDITY_EMAIL)
				.orElseGet(() -> userRepository.save(new User(LIQUIDITY_NAME, LIQUIDITY_EMAIL, LIQUIDITY_PASSWORD_HASH)));

		List<Account> accounts = accountRepository.findByOwnerId(liquidityUser.getId());
		if (!accounts.isEmpty()) {
			return accounts.get(0);
		}

		Account account = new Account(AccountType.INVESTMENT, Currency.USD, 0.0, liquidityUser);
		liquidityUser.addAccount(account);
		return accountRepository.save(account);
	}

	private void ensureSellableHolding(Account account, Asset asset, double requiredQuantity, double referencePrice) {
		Holding holding = holdingRepository.findByAccountIdAndAssetId(account.getId(), asset.getId());
		double replenishAmount = Math.max(MIN_HOLDING_BUFFER, requiredQuantity * 5.0);

		if (holding == null) {
			double avg = referencePrice > 0 ? referencePrice : 1.0;
			Holding created = new Holding(account, asset, replenishAmount, avg);
			account.addHolding(created);
			accountRepository.save(account);
			return;
		}

		if (holding.getQuantity() >= requiredQuantity) {
			return;
		}

		double existingQty = holding.getQuantity();
		double existingAvg = holding.getAveragePrice();
		double replenishPrice = referencePrice > 0 ? referencePrice : existingAvg;
		double updatedQty = existingQty + replenishAmount;
		double updatedAvg = ((existingQty * existingAvg) + (replenishAmount * replenishPrice)) / updatedQty;

		holding.setQuantity(updatedQty);
		holding.setAveragePrice(updatedAvg);
		holdingRepository.save(holding);
	}
}
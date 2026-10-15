package com.jarbis.brokerage.controller;

import com.jarbis.brokerage.dto.request.HoldingRequestDto;
import com.jarbis.brokerage.dto.response.HoldingResponseDto;
import com.jarbis.brokerage.entity.Account;
import com.jarbis.brokerage.entity.Asset;
import com.jarbis.brokerage.entity.Holding;
import com.jarbis.brokerage.exception.AccountNotFoundException;
import com.jarbis.brokerage.exception.HoldingNotFoundException;
import com.jarbis.brokerage.exception.InvalidOrderRequestException;
import com.jarbis.brokerage.repository.AccountRepository;
import com.jarbis.brokerage.repository.AssetRepository;
import com.jarbis.brokerage.repository.HoldingRepository;
import com.jarbis.brokerage.service.HoldingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/holdings")
public class HoldingController {

	private final HoldingRepository holdingRepository;
	private final AccountRepository accountRepository;
	private final AssetRepository assetRepository;
	private final HoldingService holdingService;

	@Autowired
	public HoldingController(HoldingRepository holdingRepository, AccountRepository accountRepository,
			AssetRepository assetRepository, HoldingService holdingService) {
		this.holdingRepository = holdingRepository;
		this.accountRepository = accountRepository;
		this.assetRepository = assetRepository;
		this.holdingService = holdingService;
	}

	@PostMapping
	public ResponseEntity<HoldingResponseDto> createHolding(@RequestBody HoldingRequestDto requestDto) {
		if (requestDto.getAccountId() == null || requestDto.getAssetId() == null || requestDto.getQuantity() == null
				|| requestDto.getAveragePrice() == null) {
			throw new InvalidOrderRequestException("Account ID, Asset ID, quantity, and average price are required");
		}

		if (requestDto.getQuantity() <= 0) {
			throw new InvalidOrderRequestException("Quantity must be greater than zero");
		}

		if (requestDto.getAveragePrice() < 0) {
			throw new InvalidOrderRequestException("Average price cannot be negative");
		}

		Account account = accountRepository.findById(requestDto.getAccountId()).orElseThrow(
				() -> new AccountNotFoundException("Account not found with id: " + requestDto.getAccountId()));

		Asset asset = assetRepository.findById(requestDto.getAssetId()).orElseThrow(
				() -> new InvalidOrderRequestException("Asset not found with id: " + requestDto.getAssetId()));

		// Check if holding already exists for this account and asset
		Holding existingHolding = holdingRepository.findByAccountIdAndAssetId(requestDto.getAccountId(),
				requestDto.getAssetId());
		if (existingHolding != null) {
			throw new InvalidOrderRequestException("Holding already exists for account " + requestDto.getAccountId()
					+ " and asset " + requestDto.getAssetId());
		}

		// Create new holding
		Holding holding = new Holding(account, asset, requestDto.getQuantity(), requestDto.getAveragePrice());
		Holding savedHolding = holdingRepository.save(holding);

		return ResponseEntity.status(HttpStatus.CREATED).body(convertToDto(savedHolding));
	}

	@PutMapping("/{holdingId}")
	public ResponseEntity<HoldingResponseDto> updateHolding(@PathVariable Long holdingId,
			@RequestBody HoldingRequestDto requestDto) {
		// Fetch existing holding
		Holding holding = holdingRepository.findById(holdingId)
				.orElseThrow(() -> new HoldingNotFoundException("Holding not found with id: " + holdingId));

		// Update quantity if provided
		if (requestDto.getQuantity() != null) {
			if (requestDto.getQuantity() <= 0) {
				throw new InvalidOrderRequestException("Quantity must be greater than zero");
			}
			holding.setQuantity(requestDto.getQuantity());
		}

		// Update average price if provided
		if (requestDto.getAveragePrice() != null) {
			if (requestDto.getAveragePrice() < 0) {
				throw new InvalidOrderRequestException("Average price cannot be negative");
			}
			holding.setAveragePrice(requestDto.getAveragePrice());
		}

		Holding updatedHolding = holdingRepository.save(holding);
		return ResponseEntity.ok(convertToDto(updatedHolding));
	}

	@GetMapping("/{holdingId}")
	public ResponseEntity<HoldingResponseDto> getHoldingById(@PathVariable Long holdingId) {
		Holding holding = holdingRepository.findById(holdingId)
				.orElseThrow(() -> new HoldingNotFoundException("Holding not found with id: " + holdingId));
		return ResponseEntity.ok(convertToDto(holding));
	}

	@GetMapping("/account/{accountId}")
	public ResponseEntity<List<HoldingResponseDto>> getHoldingsByAccount(@PathVariable Long accountId) {
		List<Holding> holdings = holdingRepository.findByAccountId(accountId);
		List<HoldingResponseDto> dtos = holdings.stream().map(this::convertToDto).collect(Collectors.toList());
		return ResponseEntity.ok(dtos);
	}

	@GetMapping("/{holdingId}/market-value")
	public ResponseEntity<Double> getMarketValue(@PathVariable Long holdingId) {
		Holding holding = holdingRepository.findById(holdingId)
				.orElseThrow(() -> new HoldingNotFoundException("Holding not found with id: " + holdingId));
		double marketValue = holdingService.getMarketValue(holding);
		return ResponseEntity.ok(marketValue);
	}

	@GetMapping("/{holdingId}/cost-basis")
	public ResponseEntity<Double> getCostBasis(@PathVariable Long holdingId) {
		Holding holding = holdingRepository.findById(holdingId)
				.orElseThrow(() -> new HoldingNotFoundException("Holding not found with id: " + holdingId));
		double costBasis = holdingService.getCostBasis(holding);
		return ResponseEntity.ok(costBasis);
	}

	@GetMapping("/{holdingId}/pnl")
	public ResponseEntity<Double> getUnrealizedPnL(@PathVariable Long holdingId) {
		Holding holding = holdingRepository.findById(holdingId)
				.orElseThrow(() -> new HoldingNotFoundException("Holding not found with id: " + holdingId));
		double pnl = holdingService.computeUnrealizedPnL(holding);
		return ResponseEntity.ok(pnl);
	}

	@GetMapping("/{holdingId}/pnl-percentage")
	public ResponseEntity<Double> getUnrealizedPnLPercentage(@PathVariable Long holdingId) {
		Holding holding = holdingRepository.findById(holdingId)
				.orElseThrow(() -> new HoldingNotFoundException("Holding not found with id: " + holdingId));
		double pnlPercentage = holdingService.computeUnrealizedPnLPercentage(holding);
		return ResponseEntity.ok(pnlPercentage);
	}

	@GetMapping("/{holdingId}/is-profitable")
	public ResponseEntity<Boolean> isProfitable(@PathVariable Long holdingId) {
		Holding holding = holdingRepository.findById(holdingId)
				.orElseThrow(() -> new HoldingNotFoundException("Holding not found with id: " + holdingId));
		boolean profitable = holdingService.isProfitable(holding);
		return ResponseEntity.ok(profitable);
	}

	private HoldingResponseDto convertToDto(Holding holding) {
		HoldingResponseDto dto = new HoldingResponseDto();
		dto.setId(holding.getId());
		dto.setAccountId(holding.getAccount().getId());
		dto.setAssetId(holding.getAsset().getId());
		dto.setAssetName(holding.getAsset().getName());
		dto.setQuantity(holding.getQuantity());
		dto.setAveragePrice(holding.getAveragePrice());
		dto.setMarketValue(holdingService.getMarketValue(holding));
		dto.setCostBasis(holdingService.getCostBasis(holding));
		dto.setUnrealizedPnL(holdingService.computeUnrealizedPnL(holding));
		dto.setUnrealizedPnLPercentage(holdingService.computeUnrealizedPnLPercentage(holding));
		dto.setIsProfitable(holdingService.isProfitable(holding));
		return dto;
	}
}

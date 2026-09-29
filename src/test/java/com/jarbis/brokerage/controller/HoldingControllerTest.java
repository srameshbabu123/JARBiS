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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for HoldingController. Tests cover creation, retrieval, updates,
 * analytics calculations, and edge cases.
 */
class HoldingControllerTest {

	private HoldingRepository holdingRepository;
	private AccountRepository accountRepository;
	private AssetRepository assetRepository;
	private HoldingService holdingService;
	private HoldingController holdingController;

	@BeforeEach
	void setUp() {
		holdingRepository = mock(HoldingRepository.class);
		accountRepository = mock(AccountRepository.class);
		assetRepository = mock(AssetRepository.class);
		holdingService = mock(HoldingService.class);
		holdingController = new HoldingController(holdingRepository, accountRepository, assetRepository,
				holdingService);
	}

	@Nested
	class CreateHoldingTests {
		@Test
		void createHoldingSuccessfully() {
			// Arrange
			HoldingRequestDto requestDto = new HoldingRequestDto(1L, 5L, 10.0, 150.0);

			Account account = mock(Account.class);
			when(account.getId()).thenReturn(1L);

			Asset asset = mock(Asset.class);
			when(asset.getId()).thenReturn(5L);
			when(asset.getName()).thenReturn("AAPL");

			Holding holding = new Holding(account, asset, 10.0, 150.0);
			Holding savedHolding = mock(Holding.class);
			when(savedHolding.getId()).thenReturn(1L);
			when(savedHolding.getAccount()).thenReturn(account);
			when(savedHolding.getAsset()).thenReturn(asset);
			when(savedHolding.getQuantity()).thenReturn(10.0);
			when(savedHolding.getAveragePrice()).thenReturn(150.0);

			when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
			when(assetRepository.findById(5L)).thenReturn(Optional.of(asset));
			when(holdingRepository.findByAccountIdAndAssetId(1L, 5L)).thenReturn(null);
			when(holdingRepository.save(any(Holding.class))).thenReturn(savedHolding);
			when(holdingService.getMarketValue(savedHolding)).thenReturn(1600.0);
			when(holdingService.getCostBasis(savedHolding)).thenReturn(1500.0);
			when(holdingService.computeUnrealizedPnL(savedHolding)).thenReturn(100.0);
			when(holdingService.computeUnrealizedPnLPercentage(savedHolding)).thenReturn(6.67);
			when(holdingService.isProfitable(savedHolding)).thenReturn(true);

			// Act
			ResponseEntity<HoldingResponseDto> response = holdingController.createHolding(requestDto);

			// Assert
			assertEquals(HttpStatus.CREATED, response.getStatusCode());
			HoldingResponseDto dto = response.getBody();
			assertNotNull(dto);
			assertEquals(1L, dto.getId());
			assertEquals(1L, dto.getAccountId());
			assertEquals(5L, dto.getAssetId());
			assertEquals("AAPL", dto.getAssetName());
			assertEquals(10.0, dto.getQuantity());
			assertEquals(150.0, dto.getAveragePrice());
		}

		@Test
		void createHoldingThrowsExceptionWhenAccountNotFound() {
			// Arrange
			HoldingRequestDto requestDto = new HoldingRequestDto(999L, 5L, 10.0, 150.0);

			when(accountRepository.findById(999L)).thenReturn(Optional.empty());

			// Act & Assert
			assertThrows(AccountNotFoundException.class, () -> holdingController.createHolding(requestDto));
		}

		@Test
		void createHoldingThrowsExceptionWhenAssetNotFound() {
			// Arrange
			HoldingRequestDto requestDto = new HoldingRequestDto(1L, 999L, 10.0, 150.0);

			Account account = mock(Account.class);
			when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
			when(assetRepository.findById(999L)).thenReturn(Optional.empty());

			// Act & Assert
			assertThrows(InvalidOrderRequestException.class, () -> holdingController.createHolding(requestDto));
		}

		@Test
		void createHoldingThrowsExceptionWhenHoldingAlreadyExists() {
			// Arrange
			HoldingRequestDto requestDto = new HoldingRequestDto(1L, 5L, 10.0, 150.0);

			Account account = mock(Account.class);
			Asset asset = mock(Asset.class);
			Holding existingHolding = mock(Holding.class);

			when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
			when(assetRepository.findById(5L)).thenReturn(Optional.of(asset));
			when(holdingRepository.findByAccountIdAndAssetId(1L, 5L)).thenReturn(existingHolding);

			// Act & Assert
			assertThrows(InvalidOrderRequestException.class, () -> holdingController.createHolding(requestDto));
		}

		@Test
		void createHoldingThrowsExceptionWhenQuantityIsZero() {
			// Arrange
			HoldingRequestDto requestDto = new HoldingRequestDto(1L, 5L, 0.0, 150.0);

			// Act & Assert
			assertThrows(InvalidOrderRequestException.class, () -> holdingController.createHolding(requestDto));
		}

		@Test
		void createHoldingThrowsExceptionWhenQuantityIsNegative() {
			// Arrange
			HoldingRequestDto requestDto = new HoldingRequestDto(1L, 5L, -10.0, 150.0);

			// Act & Assert
			assertThrows(InvalidOrderRequestException.class, () -> holdingController.createHolding(requestDto));
		}

		@Test
		void createHoldingThrowsExceptionWhenAveragePriceIsNegative() {
			// Arrange
			HoldingRequestDto requestDto = new HoldingRequestDto(1L, 5L, 10.0, -150.0);

			// Act & Assert
			assertThrows(InvalidOrderRequestException.class, () -> holdingController.createHolding(requestDto));
		}

		@Test
		void createHoldingThrowsExceptionWhenFieldsAreNull() {
			// Arrange
			HoldingRequestDto requestDto = new HoldingRequestDto(null, 5L, 10.0, 150.0);

			// Act & Assert
			assertThrows(InvalidOrderRequestException.class, () -> holdingController.createHolding(requestDto));
		}
	}

	@Nested
	class UpdateHoldingTests {
		@Test
		void updateHoldingQuantity() {
			// Arrange
			HoldingRequestDto requestDto = new HoldingRequestDto();
			requestDto.setQuantity(20.0);

			Holding holding = mock(Holding.class);
			when(holding.getId()).thenReturn(1L);
			when(holdingRepository.findById(1L)).thenReturn(Optional.of(holding));

			Holding updatedHolding = mock(Holding.class);
			when(updatedHolding.getId()).thenReturn(1L);
			when(updatedHolding.getAccount()).thenReturn(mock(Account.class));
			when(updatedHolding.getAsset()).thenReturn(mock(Asset.class));
			when(updatedHolding.getAccount().getId()).thenReturn(1L);
			when(updatedHolding.getAsset().getId()).thenReturn(5L);
			when(updatedHolding.getAsset().getName()).thenReturn("AAPL");
			when(updatedHolding.getQuantity()).thenReturn(20.0);
			when(updatedHolding.getAveragePrice()).thenReturn(150.0);

			when(holdingRepository.save(holding)).thenReturn(updatedHolding);
			when(holdingService.getMarketValue(updatedHolding)).thenReturn(3200.0);
			when(holdingService.getCostBasis(updatedHolding)).thenReturn(3000.0);
			when(holdingService.computeUnrealizedPnL(updatedHolding)).thenReturn(200.0);
			when(holdingService.computeUnrealizedPnLPercentage(updatedHolding)).thenReturn(6.67);
			when(holdingService.isProfitable(updatedHolding)).thenReturn(true);

			// Act
			ResponseEntity<HoldingResponseDto> response = holdingController.updateHolding(1L, requestDto);

			// Assert
			assertEquals(HttpStatus.OK, response.getStatusCode());
			verify(holding).setQuantity(20.0);
		}

		@Test
		void updateHoldingAveragePrice() {
			// Arrange
			HoldingRequestDto requestDto = new HoldingRequestDto();
			requestDto.setAveragePrice(160.0);

			Holding holding = mock(Holding.class);
			when(holding.getId()).thenReturn(1L);
			when(holdingRepository.findById(1L)).thenReturn(Optional.of(holding));

			Holding updatedHolding = mock(Holding.class);
			when(updatedHolding.getId()).thenReturn(1L);
			when(updatedHolding.getAccount()).thenReturn(mock(Account.class));
			when(updatedHolding.getAsset()).thenReturn(mock(Asset.class));
			when(updatedHolding.getAccount().getId()).thenReturn(1L);
			when(updatedHolding.getAsset().getId()).thenReturn(5L);
			when(updatedHolding.getAsset().getName()).thenReturn("AAPL");
			when(updatedHolding.getQuantity()).thenReturn(10.0);
			when(updatedHolding.getAveragePrice()).thenReturn(160.0);

			when(holdingRepository.save(holding)).thenReturn(updatedHolding);
			when(holdingService.getMarketValue(updatedHolding)).thenReturn(1600.0);
			when(holdingService.getCostBasis(updatedHolding)).thenReturn(1600.0);
			when(holdingService.computeUnrealizedPnL(updatedHolding)).thenReturn(0.0);
			when(holdingService.computeUnrealizedPnLPercentage(updatedHolding)).thenReturn(0.0);
			when(holdingService.isProfitable(updatedHolding)).thenReturn(false);

			// Act
			ResponseEntity<HoldingResponseDto> response = holdingController.updateHolding(1L, requestDto);

			// Assert
			assertEquals(HttpStatus.OK, response.getStatusCode());
			verify(holding).setAveragePrice(160.0);
		}

		@Test
		void updateHoldingBothFields() {
			// Arrange
			HoldingRequestDto requestDto = new HoldingRequestDto();
			requestDto.setQuantity(20.0);
			requestDto.setAveragePrice(160.0);

			Holding holding = mock(Holding.class);
			when(holding.getId()).thenReturn(1L);
			when(holdingRepository.findById(1L)).thenReturn(Optional.of(holding));

			Holding updatedHolding = mock(Holding.class);
			when(updatedHolding.getId()).thenReturn(1L);
			when(updatedHolding.getAccount()).thenReturn(mock(Account.class));
			when(updatedHolding.getAsset()).thenReturn(mock(Asset.class));
			when(updatedHolding.getAccount().getId()).thenReturn(1L);
			when(updatedHolding.getAsset().getId()).thenReturn(5L);
			when(updatedHolding.getAsset().getName()).thenReturn("AAPL");
			when(updatedHolding.getQuantity()).thenReturn(20.0);
			when(updatedHolding.getAveragePrice()).thenReturn(160.0);

			when(holdingRepository.save(holding)).thenReturn(updatedHolding);
			when(holdingService.getMarketValue(updatedHolding)).thenReturn(3200.0);
			when(holdingService.getCostBasis(updatedHolding)).thenReturn(3200.0);
			when(holdingService.computeUnrealizedPnL(updatedHolding)).thenReturn(0.0);
			when(holdingService.computeUnrealizedPnLPercentage(updatedHolding)).thenReturn(0.0);
			when(holdingService.isProfitable(updatedHolding)).thenReturn(false);

			// Act
			ResponseEntity<HoldingResponseDto> response = holdingController.updateHolding(1L, requestDto);

			// Assert
			assertEquals(HttpStatus.OK, response.getStatusCode());
			verify(holding).setQuantity(20.0);
			verify(holding).setAveragePrice(160.0);
		}

		@Test
		void updateHoldingThrowsExceptionWhenNotFound() {
			// Arrange
			HoldingRequestDto requestDto = new HoldingRequestDto();
			requestDto.setQuantity(20.0);

			when(holdingRepository.findById(999L)).thenReturn(Optional.empty());

			// Act & Assert
			assertThrows(HoldingNotFoundException.class, () -> holdingController.updateHolding(999L, requestDto));
		}

		@Test
		void updateHoldingThrowsExceptionWhenQuantityIsNegative() {
			// Arrange
			HoldingRequestDto requestDto = new HoldingRequestDto();
			requestDto.setQuantity(-10.0);

			Holding holding = mock(Holding.class);
			when(holdingRepository.findById(1L)).thenReturn(Optional.of(holding));

			// Act & Assert
			assertThrows(InvalidOrderRequestException.class, () -> holdingController.updateHolding(1L, requestDto));
		}

		@Test
		void updateHoldingThrowsExceptionWhenAveragePriceIsNegative() {
			// Arrange
			HoldingRequestDto requestDto = new HoldingRequestDto();
			requestDto.setAveragePrice(-150.0);

			Holding holding = mock(Holding.class);
			when(holdingRepository.findById(1L)).thenReturn(Optional.of(holding));

			// Act & Assert
			assertThrows(InvalidOrderRequestException.class, () -> holdingController.updateHolding(1L, requestDto));
		}
	}

	@Nested
	class GetHoldingTests {
		@Test
		void getHoldingByIdReturnsHoldingWithAnalytics() {
			Account account = mock(Account.class);
			when(account.getId()).thenReturn(1L);

			Asset asset = mock(Asset.class);
			when(asset.getId()).thenReturn(5L);
			when(asset.getName()).thenReturn("AAPL");

			Holding holding = mock(Holding.class);
			when(holding.getId()).thenReturn(1L);
			when(holding.getAccount()).thenReturn(account);
			when(holding.getAsset()).thenReturn(asset);
			when(holding.getQuantity()).thenReturn(10.0);
			when(holding.getAveragePrice()).thenReturn(150.0);

			when(holdingRepository.findById(1L)).thenReturn(Optional.of(holding));
			when(holdingService.getMarketValue(holding)).thenReturn(1600.0);
			when(holdingService.getCostBasis(holding)).thenReturn(1500.0);
			when(holdingService.computeUnrealizedPnL(holding)).thenReturn(100.0);
			when(holdingService.computeUnrealizedPnLPercentage(holding)).thenReturn(6.67);
			when(holdingService.isProfitable(holding)).thenReturn(true);

			ResponseEntity<HoldingResponseDto> response = holdingController.getHoldingById(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			HoldingResponseDto dto = response.getBody();
			assertNotNull(dto);
			assertEquals(1L, dto.getId());
			assertEquals(1L, dto.getAccountId());
			assertEquals(5L, dto.getAssetId());
			assertEquals("AAPL", dto.getAssetName());
			assertEquals(10.0, dto.getQuantity());
			assertEquals(150.0, dto.getAveragePrice());
			assertEquals(1600.0, dto.getMarketValue());
			assertEquals(1500.0, dto.getCostBasis());
			assertEquals(100.0, dto.getUnrealizedPnL());
			assertEquals(6.67, dto.getUnrealizedPnLPercentage());
			assertTrue(dto.getIsProfitable());
		}

		@Test
		void getHoldingByIdThrowsExceptionWhenNotFound() {
			when(holdingRepository.findById(999L)).thenReturn(Optional.empty());

			assertThrows(HoldingNotFoundException.class, () -> holdingController.getHoldingById(999L));
		}
	}

	@Nested
	class GetHoldingsByAccountTests {
		@Test
		void getHoldingsByAccountReturnsMultipleHoldings() {
			Account account = mock(Account.class);
			when(account.getId()).thenReturn(1L);

			Asset asset1 = mock(Asset.class);
			when(asset1.getId()).thenReturn(5L);
			when(asset1.getName()).thenReturn("AAPL");

			Asset asset2 = mock(Asset.class);
			when(asset2.getId()).thenReturn(6L);
			when(asset2.getName()).thenReturn("GOOGL");

			Holding holding1 = mock(Holding.class);
			when(holding1.getId()).thenReturn(1L);
			when(holding1.getAccount()).thenReturn(account);
			when(holding1.getAsset()).thenReturn(asset1);
			when(holding1.getQuantity()).thenReturn(10.0);
			when(holding1.getAveragePrice()).thenReturn(150.0);

			Holding holding2 = mock(Holding.class);
			when(holding2.getId()).thenReturn(2L);
			when(holding2.getAccount()).thenReturn(account);
			when(holding2.getAsset()).thenReturn(asset2);
			when(holding2.getQuantity()).thenReturn(5.0);
			when(holding2.getAveragePrice()).thenReturn(2800.0);

			List<Holding> holdings = new ArrayList<>();
			holdings.add(holding1);
			holdings.add(holding2);

			when(holdingRepository.findByAccountId(1L)).thenReturn(holdings);
			when(holdingService.getMarketValue(holding1)).thenReturn(1600.0);
			when(holdingService.getCostBasis(holding1)).thenReturn(1500.0);
			when(holdingService.computeUnrealizedPnL(holding1)).thenReturn(100.0);
			when(holdingService.computeUnrealizedPnLPercentage(holding1)).thenReturn(6.67);
			when(holdingService.isProfitable(holding1)).thenReturn(true);

			when(holdingService.getMarketValue(holding2)).thenReturn(14000.0);
			when(holdingService.getCostBasis(holding2)).thenReturn(14000.0);
			when(holdingService.computeUnrealizedPnL(holding2)).thenReturn(0.0);
			when(holdingService.computeUnrealizedPnLPercentage(holding2)).thenReturn(0.0);
			when(holdingService.isProfitable(holding2)).thenReturn(false);

			ResponseEntity<List<HoldingResponseDto>> response = holdingController.getHoldingsByAccount(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			List<HoldingResponseDto> dtos = response.getBody();
			assertNotNull(dtos);
			assertEquals(2, dtos.size());
			assertEquals("AAPL", dtos.get(0).getAssetName());
			assertEquals("GOOGL", dtos.get(1).getAssetName());
		}

		@Test
		void getHoldingsByAccountReturnsEmptyListWhenNoHoldings() {
			when(holdingRepository.findByAccountId(1L)).thenReturn(new ArrayList<>());

			ResponseEntity<List<HoldingResponseDto>> response = holdingController.getHoldingsByAccount(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			List<HoldingResponseDto> dtos = response.getBody();
			assertNotNull(dtos);
			assertTrue(dtos.isEmpty());
		}
	}

	@Nested
	class MarketValueTests {
		@Test
		void getMarketValueReturnsCorrectValue() {
			Account account = mock(Account.class);
			Asset asset = mock(Asset.class);

			Holding holding = mock(Holding.class);
			when(holding.getId()).thenReturn(1L);

			when(holdingRepository.findById(1L)).thenReturn(Optional.of(holding));
			when(holdingService.getMarketValue(holding)).thenReturn(1600.0);

			ResponseEntity<Double> response = holdingController.getMarketValue(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(1600.0, response.getBody());
		}

		@Test
		void getMarketValueThrowsExceptionWhenHoldingNotFound() {
			when(holdingRepository.findById(999L)).thenReturn(Optional.empty());

			assertThrows(HoldingNotFoundException.class, () -> holdingController.getMarketValue(999L));
		}
	}

	@Nested
	class CostBasisTests {
		@Test
		void getCostBasisReturnsCorrectValue() {
			Holding holding = mock(Holding.class);
			when(holding.getId()).thenReturn(1L);

			when(holdingRepository.findById(1L)).thenReturn(Optional.of(holding));
			when(holdingService.getCostBasis(holding)).thenReturn(1500.0);

			ResponseEntity<Double> response = holdingController.getCostBasis(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(1500.0, response.getBody());
		}
	}

	@Nested
	class UnrealizedPnLTests {
		@Test
		void getUnrealizedPnLReturnsPositiveValueWhenProfitable() {
			Holding holding = mock(Holding.class);
			when(holding.getId()).thenReturn(1L);

			when(holdingRepository.findById(1L)).thenReturn(Optional.of(holding));
			when(holdingService.computeUnrealizedPnL(holding)).thenReturn(100.0);

			ResponseEntity<Double> response = holdingController.getUnrealizedPnL(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(100.0, response.getBody());
		}

		@Test
		void getUnrealizedPnLReturnsNegativeValueWhenUnprofitable() {
			Holding holding = mock(Holding.class);
			when(holding.getId()).thenReturn(1L);

			when(holdingRepository.findById(1L)).thenReturn(Optional.of(holding));
			when(holdingService.computeUnrealizedPnL(holding)).thenReturn(-100.0);

			ResponseEntity<Double> response = holdingController.getUnrealizedPnL(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(-100.0, response.getBody());
		}

		@Test
		void getPnLPercentageReturnsCorrectPercentage() {
			Holding holding = mock(Holding.class);
			when(holding.getId()).thenReturn(1L);

			when(holdingRepository.findById(1L)).thenReturn(Optional.of(holding));
			when(holdingService.computeUnrealizedPnLPercentage(holding)).thenReturn(6.67);

			ResponseEntity<Double> response = holdingController.getUnrealizedPnLPercentage(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(6.67, response.getBody());
		}
	}

	@Nested
	class ProfitabilityTests {
		@Test
		void isProfitableReturnsTrueWhenProfitable() {
			Holding holding = mock(Holding.class);
			when(holding.getId()).thenReturn(1L);

			when(holdingRepository.findById(1L)).thenReturn(Optional.of(holding));
			when(holdingService.isProfitable(holding)).thenReturn(true);

			ResponseEntity<Boolean> response = holdingController.isProfitable(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertTrue(response.getBody());
		}

		@Test
		void isProfitableReturnsFalseWhenUnprofitable() {
			Holding holding = mock(Holding.class);
			when(holding.getId()).thenReturn(1L);

			when(holdingRepository.findById(1L)).thenReturn(Optional.of(holding));
			when(holdingService.isProfitable(holding)).thenReturn(false);

			ResponseEntity<Boolean> response = holdingController.isProfitable(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertFalse(response.getBody());
		}
	}
}

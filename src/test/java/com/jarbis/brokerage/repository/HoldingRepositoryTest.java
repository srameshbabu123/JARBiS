package com.jarbis.brokerage.repository;

import com.jarbis.brokerage.entity.Account;
import com.jarbis.brokerage.entity.Holding;
import com.jarbis.brokerage.entity.StockAsset;
import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.enums.AccountType;
import com.jarbis.brokerage.enums.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Holding Repository Tests")
class HoldingRepositoryTest {

    @Mock
    private HoldingRepository holdingRepository;

    private Holding testHolding;
    private Account testAccount;
    private StockAsset testAsset;

    @BeforeEach
    void setUp() {
        User user = new User("Test User", "test@example.com", "hashed");

        testAccount = new Account(AccountType.INVESTMENT, Currency.USD, 10000.0, user);

        testAsset = new StockAsset("Apple Inc.", 150.00);

        testHolding = new Holding(testAccount, testAsset, 100.0, 150.00);
    }

    @Nested
    @DisplayName("Create and Save Holding Tests")
    class CreateHoldingTests {

        @Test
        @DisplayName("Should save holding and generate ID")
        void testSaveHolding() {
            Holding holding = new Holding(testAccount, testAsset, 100.0, 150.00);

            when(holdingRepository.save(any(Holding.class))).thenReturn(testHolding);

            Holding result = holdingRepository.save(holding);

            assertEquals(100.0, result.getQuantity());
            assertEquals(150.00, result.getAveragePrice());
            verify(holdingRepository).save(any(Holding.class));
        }

        @Test
        @DisplayName("Should save multiple holdings for same account")
        void testSaveMultipleHoldings() {
            StockAsset asset2 = new StockAsset("Microsoft Corp.", 300.00);
            Holding holding2 = new Holding(testAccount, asset2, 50.0, 300.00);

            when(holdingRepository.save(any(Holding.class))).thenReturn(testHolding, holding2);

            holdingRepository.save(testHolding);
            holdingRepository.save(holding2);

            verify(holdingRepository, times(2)).save(any(Holding.class));
        }
    }

    @Nested
    @DisplayName("Find Holding Tests")
    class FindHoldingTests {

        @Test
        @DisplayName("Should find holding by ID")
        void testFindById() {
            when(holdingRepository.findById(1L)).thenReturn(Optional.of(testHolding));

            Optional<Holding> found = holdingRepository.findById(1L);

            assertTrue(found.isPresent());
            assertEquals(100.0, found.get().getQuantity());
        }

        @Test
        @DisplayName("Should return empty when holding not found by ID")
        void testFindByIdNotFound() {
            when(holdingRepository.findById(999L)).thenReturn(Optional.empty());

            Optional<Holding> found = holdingRepository.findById(999L);

            assertTrue(found.isEmpty());
        }

        @Test
        @DisplayName("Should find holdings by account ID")
        void testFindByAccountId() {
            List<Holding> holdings = List.of(testHolding);

            when(holdingRepository.findByAccountId(1L)).thenReturn(holdings);

            List<Holding> result = holdingRepository.findByAccountId(1L);

            assertEquals(1, result.size());
            assertEquals(testAccount.getId(), result.get(0).getAccount().getId());
        }

        @Test
        @DisplayName("Should return empty list when account has no holdings")
        void testFindByAccountIdNoHoldings() {
            when(holdingRepository.findByAccountId(999L)).thenReturn(List.of());

            List<Holding> holdings = holdingRepository.findByAccountId(999L);

            assertTrue(holdings.isEmpty());
        }

        @Test
        @DisplayName("Should find holding by account and asset")
        void testFindByAccountIdAndAssetId() {
            when(holdingRepository.findByAccountIdAndAssetId(1L, 1L)).thenReturn(testHolding);

            Holding found = holdingRepository.findByAccountIdAndAssetId(1L, 1L);

            assertNotNull(found);
            assertEquals(100.0, found.getQuantity());
        }

        @Test
        @DisplayName("Should return null when holding does not exist")
        void testFindByAccountIdAndAssetIdNotFound() {
            when(holdingRepository.findByAccountIdAndAssetId(1L, 1L)).thenReturn(null);

            Holding found = holdingRepository.findByAccountIdAndAssetId(1L, 1L);

            assertNull(found);
        }
    }

    @Nested
    @DisplayName("Update Holding Tests")
    class UpdateHoldingTests {

        @Test
        @DisplayName("Should update holding quantity")
        void testUpdateQuantity() {
            Holding updatedHolding = new Holding(testAccount, testAsset, 150.0, 150.00);

            when(holdingRepository.save(any(Holding.class))).thenReturn(updatedHolding);

            Holding result = holdingRepository.save(updatedHolding);

            assertEquals(150.0, result.getQuantity());
            verify(holdingRepository).save(any(Holding.class));
        }

        @Test
        @DisplayName("Should update holding average price")
        void testUpdateAveragePrice() {
            Holding updatedHolding = new Holding(testAccount, testAsset, 100.0, 155.00);

            when(holdingRepository.save(any(Holding.class))).thenReturn(updatedHolding);

            Holding result = holdingRepository.save(updatedHolding);

            assertEquals(155.00, result.getAveragePrice());
            verify(holdingRepository).save(any(Holding.class));
        }
    }

    @Nested
    @DisplayName("Delete Holding Tests")
    class DeleteHoldingTests {

        @Test
        @DisplayName("Should delete holding by entity")
        void testDeleteHolding() {
            holdingRepository.delete(testHolding);

            verify(holdingRepository).delete(testHolding);
        }

        @Test
        @DisplayName("Should delete holding by ID")
        void testDeleteById() {
            holdingRepository.deleteById(1L);

            verify(holdingRepository).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("FindAll and Count Tests")
    class FindAllAndCountTests {

        @Test
        @DisplayName("Should retrieve all holdings")
        void testFindAll() {
            List<Holding> holdings = List.of(testHolding);

            when(holdingRepository.findAll()).thenReturn(holdings);

            List<Holding> result = holdingRepository.findAll();

            assertTrue(result.size() >= 1);
        }

        @Test
        @DisplayName("Should count all holdings")
        void testCount() {
            when(holdingRepository.count()).thenReturn(1L);

            long count = holdingRepository.count();

            assertTrue(count >= 1);
        }
    }
}


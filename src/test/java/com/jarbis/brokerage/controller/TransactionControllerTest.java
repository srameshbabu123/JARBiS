package com.jarbis.brokerage.controller;

import com.jarbis.brokerage.dto.response.TransactionResponseDto;
import com.jarbis.brokerage.entity.Asset;
import com.jarbis.brokerage.entity.Order;
import com.jarbis.brokerage.entity.Transaction;
import com.jarbis.brokerage.enums.TransactionStatus;
import com.jarbis.brokerage.exception.TransactionNotFoundException;
import com.jarbis.brokerage.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for TransactionController following TDD principles. Tests cover
 * transaction lifecycle, execution, completion, and filtering.
 */
class TransactionControllerTest {

	private TransactionService transactionService;
	private TransactionController transactionController;

	@BeforeEach
	void setUp() {
		transactionService = mock(TransactionService.class);
		transactionController = new TransactionController(transactionService);
	}

	@Test
	void getTransactionByIdReturnsTransactionFromService() {
		Transaction expectedTransaction = new Transaction();
		expectedTransaction.setQuantity(100.0);
		expectedTransaction.setExecutionPrice(50.0);
		expectedTransaction.setStatus(TransactionStatus.PENDING);

		when(transactionService.getTransactionById(1L)).thenReturn(expectedTransaction);

		ResponseEntity<Transaction> response = transactionController.getTransactionById(1L);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(TransactionStatus.PENDING, response.getBody().getStatus());
		verify(transactionService).getTransactionById(1L);
	}

	@Test
	void getAllTransactionsReturnsListFromService() {
		Transaction transaction1 = new Transaction();
		transaction1.setStatus(TransactionStatus.PENDING);
		Transaction transaction2 = new Transaction();
		transaction2.setStatus(TransactionStatus.COMPLETED);
		List<Transaction> expectedTransactions = Arrays.asList(transaction1, transaction2);

		when(transactionService.getAllTransactions()).thenReturn(expectedTransactions);

		ResponseEntity<List<Transaction>> response = transactionController.getAllTransactions();

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(2, response.getBody().size());
		verify(transactionService).getAllTransactions();
	}

	@Test
	void executeTransactionDelegatesToService() {
		transactionController.executeTransaction(1L);

		verify(transactionService).executeTransaction(1L);
	}

	@Test
	void completeTransactionDelegatesToService() {
		transactionController.completeTransaction(1L);

		verify(transactionService).completeTransaction(1L);
	}

	@Test
	void failTransactionDelegatesToService() {
		transactionController.failTransaction(1L);

		verify(transactionService).failTransaction(1L);
	}

	@Test
	void getTradeValueReturnsValueFromService() {
		when(transactionService.computeTradeValueById(1L)).thenReturn(5000.0);

		ResponseEntity<Double> response = transactionController.getTradeValue(1L);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(5000.0, response.getBody());
		verify(transactionService).computeTradeValueById(1L);
	}

	@Test
	void getOrderCountReturnsCountFromService() {
		Transaction transaction = new Transaction();
		Order order1 = new Order();
		Order order2 = new Order();
		transaction.getParticipants().add(order1);
		transaction.getParticipants().add(order2);

		when(transactionService.getTransactionById(1L)).thenReturn(transaction);
		when(transactionService.getTransactionOrderCount(transaction)).thenReturn(2);

		ResponseEntity<Integer> response = transactionController.getOrderCount(1L);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(2, response.getBody());
		verify(transactionService).getTransactionById(1L);
		verify(transactionService).getTransactionOrderCount(transaction);
	}

	@Test
	void areAllOrdersCompletedReturnsTrueWhenAllCompleted() {
		Transaction transaction = new Transaction();

		when(transactionService.getTransactionById(1L)).thenReturn(transaction);
		when(transactionService.areAllOrdersCompleted(transaction)).thenReturn(true);

		ResponseEntity<Boolean> response = transactionController.areAllOrdersCompleted(1L);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getBody());
		verify(transactionService).getTransactionById(1L);
		verify(transactionService).areAllOrdersCompleted(transaction);
	}

	@Test
	void areAllOrdersCompletedReturnsFalseWhenNotAllCompleted() {
		Transaction transaction = new Transaction();

		when(transactionService.getTransactionById(1L)).thenReturn(transaction);
		when(transactionService.areAllOrdersCompleted(transaction)).thenReturn(false);

		ResponseEntity<Boolean> response = transactionController.areAllOrdersCompleted(1L);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertFalse(response.getBody());
	}

	@Nested
	class GetTransactionTests {

		@Test
		void testGetTransactionByIdWithValidId() {
			Transaction expectedTransaction = new Transaction();
			expectedTransaction.setQuantity(100.0);
			expectedTransaction.setExecutionPrice(50.0);

			when(transactionService.getTransactionById(1L)).thenReturn(expectedTransaction);

			ResponseEntity<Transaction> response = transactionController.getTransactionById(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(100.0, response.getBody().getQuantity());
		}

		@Test
		void testGetTransactionByIdThrowsExceptionForInvalidId() {
			when(transactionService.getTransactionById(999L))
					.thenThrow(new TransactionNotFoundException("Transaction not found"));

			assertThrows(TransactionNotFoundException.class, () -> transactionController.getTransactionById(999L));
		}

		@Test
		void testGetAllTransactions() {
			Transaction transaction1 = new Transaction();
			transaction1.setQuantity(50.0);
			Transaction transaction2 = new Transaction();
			transaction2.setQuantity(100.0);
			List<Transaction> expectedTransactions = Arrays.asList(transaction1, transaction2);

			when(transactionService.getAllTransactions()).thenReturn(expectedTransactions);

			ResponseEntity<List<Transaction>> response = transactionController.getAllTransactions();

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(2, response.getBody().size());
			assertEquals(50.0, response.getBody().get(0).getQuantity());
			assertEquals(100.0, response.getBody().get(1).getQuantity());
		}

		@Test
		void testGetAllTransactionsEmpty() {
			when(transactionService.getAllTransactions()).thenReturn(new ArrayList<>());

			ResponseEntity<List<Transaction>> response = transactionController.getAllTransactions();

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(0, response.getBody().size());
		}
	}

	@Nested
	class StatusFilteringTests {

		@Test
		void testGetTransactionsByStatusPending() {
			Transaction transaction = new Transaction();
			transaction.setStatus(TransactionStatus.PENDING);
			List<Transaction> expectedTransactions = Arrays.asList(transaction);

			when(transactionService.getTransactionsByStatus(TransactionStatus.PENDING))
					.thenReturn(expectedTransactions);

			ResponseEntity<List<Transaction>> response = transactionController
					.getTransactionsByStatus(TransactionStatus.PENDING);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(1, response.getBody().size());
			assertTrue(response.getBody().stream().allMatch(t -> t.getStatus() == TransactionStatus.PENDING));
		}

		@Test
		void testGetTransactionsByStatusCompleted() {
			Transaction transaction = new Transaction();
			transaction.setStatus(TransactionStatus.COMPLETED);
			List<Transaction> expectedTransactions = Arrays.asList(transaction);

			when(transactionService.getTransactionsByStatus(TransactionStatus.COMPLETED))
					.thenReturn(expectedTransactions);

			ResponseEntity<List<Transaction>> response = transactionController
					.getTransactionsByStatus(TransactionStatus.COMPLETED);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(1, response.getBody().size());
			assertTrue(response.getBody().stream().allMatch(t -> t.getStatus() == TransactionStatus.COMPLETED));
		}

		@Test
		void testGetPendingTransactions() {
			Transaction transaction = new Transaction();
			transaction.setStatus(TransactionStatus.PENDING);
			List<Transaction> expectedTransactions = Arrays.asList(transaction);

			when(transactionService.getPendingTransactions()).thenReturn(expectedTransactions);

			ResponseEntity<List<Transaction>> response = transactionController.getPendingTransactions();

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertTrue(response.getBody().stream().allMatch(t -> t.getStatus() == TransactionStatus.PENDING));
		}

		@Test
		void testGetCompletedTransactions() {
			Transaction transaction = new Transaction();
			transaction.setStatus(TransactionStatus.COMPLETED);
			List<Transaction> expectedTransactions = Arrays.asList(transaction);

			when(transactionService.getCompletedTransactions()).thenReturn(expectedTransactions);

			ResponseEntity<List<Transaction>> response = transactionController.getCompletedTransactions();

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertTrue(response.getBody().stream().allMatch(t -> t.getStatus() == TransactionStatus.COMPLETED));
		}
	}

	@Nested
	class TransactionExecutionTests {

		@Test
		void testExecuteTransactionDelegatesToService() {
			transactionController.executeTransaction(1L);

			verify(transactionService).executeTransaction(1L);
		}

		@Test
		void testExecuteTransactionReturnsOkStatus() {
			ResponseEntity<Void> response = transactionController.executeTransaction(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertNull(response.getBody());
		}

		@Test
		void testCompleteTransactionDelegatesToService() {
			transactionController.completeTransaction(1L);

			verify(transactionService).completeTransaction(1L);
		}

		@Test
		void testCompleteTransactionReturnsOkStatus() {
			ResponseEntity<Void> response = transactionController.completeTransaction(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertNull(response.getBody());
		}

		@Test
		void testFailTransactionDelegatesToService() {
			transactionController.failTransaction(1L);

			verify(transactionService).failTransaction(1L);
		}

		@Test
		void testFailTransactionReturnsOkStatus() {
			ResponseEntity<Void> response = transactionController.failTransaction(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertNull(response.getBody());
		}

		@Test
		void testExecuteNonExistentTransactionThrowsException() {
			doThrow(new TransactionNotFoundException("Transaction not found")).when(transactionService)
					.executeTransaction(999L);

			assertThrows(TransactionNotFoundException.class, () -> transactionController.executeTransaction(999L));
		}
	}

	@Nested
	class TradeValueTests {

		@Test
		void testGetTradeValueForTransaction() {
			when(transactionService.computeTradeValueById(1L)).thenReturn(5000.0);

			ResponseEntity<Double> response = transactionController.getTradeValue(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(5000.0, response.getBody());
		}

		@Test
		void testGetTradeValueWithHighPrice() {
			when(transactionService.computeTradeValueById(1L)).thenReturn(100000.0);

			ResponseEntity<Double> response = transactionController.getTradeValue(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(100000.0, response.getBody());
		}

		@Test
		void testGetTradeValueWithSmallQuantity() {
			when(transactionService.computeTradeValueById(1L)).thenReturn(250.0);

			ResponseEntity<Double> response = transactionController.getTradeValue(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(250.0, response.getBody());
		}

		@Test
		void testGetTradeValueForNonExistentTransactionThrowsException() {
			when(transactionService.computeTradeValueById(999L))
					.thenThrow(new TransactionNotFoundException("Transaction not found"));

			assertThrows(TransactionNotFoundException.class, () -> transactionController.getTradeValue(999L));
		}
	}

	@Nested
	class OrderCountTests {

		@Test
		void testGetOrderCountWithSingleOrder() {
			Transaction transaction = new Transaction();
			Order order = new Order();
			transaction.getParticipants().add(order);

			when(transactionService.getTransactionById(1L)).thenReturn(transaction);
			when(transactionService.getTransactionOrderCount(transaction)).thenReturn(1);

			ResponseEntity<Integer> response = transactionController.getOrderCount(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(1, response.getBody());
		}

		@Test
		void testGetOrderCountWithMultipleOrders() {
			Transaction transaction = new Transaction();
			for (int i = 0; i < 5; i++) {
				Order order = new Order();
				transaction.getParticipants().add(order);
			}

			when(transactionService.getTransactionById(1L)).thenReturn(transaction);
			when(transactionService.getTransactionOrderCount(transaction)).thenReturn(5);

			ResponseEntity<Integer> response = transactionController.getOrderCount(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(5, response.getBody());
		}

		@Test
		void testGetOrderCountWithNoOrders() {
			Transaction transaction = new Transaction();

			when(transactionService.getTransactionById(1L)).thenReturn(transaction);
			when(transactionService.getTransactionOrderCount(transaction)).thenReturn(0);

			ResponseEntity<Integer> response = transactionController.getOrderCount(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals(0, response.getBody());
		}
	}

	@Nested
	class OrderCompletionTests {

		@Test
		void testAllOrdersCompletedReturnsTrue() {
			Transaction transaction = new Transaction();

			when(transactionService.getTransactionById(1L)).thenReturn(transaction);
			when(transactionService.areAllOrdersCompleted(transaction)).thenReturn(true);

			ResponseEntity<Boolean> response = transactionController.areAllOrdersCompleted(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertTrue(response.getBody());
		}

		@Test
		void testAllOrdersCompletedReturnsFalse() {
			Transaction transaction = new Transaction();

			when(transactionService.getTransactionById(1L)).thenReturn(transaction);
			when(transactionService.areAllOrdersCompleted(transaction)).thenReturn(false);

			ResponseEntity<Boolean> response = transactionController.areAllOrdersCompleted(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertFalse(response.getBody());
		}

		@Test
		void testAllOrdersCompletedWithSingleOrder() {
			Transaction transaction = new Transaction();
			Order order = new Order();
			transaction.getParticipants().add(order);

			when(transactionService.getTransactionById(1L)).thenReturn(transaction);
			when(transactionService.areAllOrdersCompleted(transaction)).thenReturn(true);

			ResponseEntity<Boolean> response = transactionController.areAllOrdersCompleted(1L);

			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertTrue(response.getBody());
		}
	}
}

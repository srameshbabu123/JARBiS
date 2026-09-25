package com.jarbis.brokerage.controller;

import com.jarbis.brokerage.dto.request.CreateAccountRequestDto;
import com.jarbis.brokerage.dto.request.UpdateAccountRequestDto;
import com.jarbis.brokerage.dto.response.AccountResponseDto;
import com.jarbis.brokerage.entity.Account;
import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.enums.AccountType;
import com.jarbis.brokerage.enums.Currency;
import com.jarbis.brokerage.exception.AccountNotFoundException;
import com.jarbis.brokerage.exception.UserNotFoundException;
import com.jarbis.brokerage.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for AccountController following TDD principles.
 * Tests cover CRUD operations, multiple currencies, and edge cases.
 */
class AccountControllerTest {

    private AccountService accountService;
    private AccountController accountController;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        accountController = new AccountController(accountService);
    }

    @Test
    void createAccountUsesExplicitInitialBalanceWhenProvided() {
        CreateAccountRequestDto requestDto = new CreateAccountRequestDto(1L, AccountType.SAVINGS, Currency.USD, 1000.0);
        Account expectedAccount = new Account(AccountType.SAVINGS, Currency.USD, 1000.0, new User());

        when(accountService.createAccount(1L, AccountType.SAVINGS, Currency.USD, 1000.0)).thenReturn(expectedAccount);

        ResponseEntity<AccountResponseDto> response = accountController.createAccount(requestDto);
        AccountResponseDto body = response.getBody();

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(body);
        assertEquals(AccountType.SAVINGS, body.getAccountType());
        assertEquals(Currency.USD, body.getCurrency());
        assertEquals(1000.0, body.getBalance());
        assertNull(body.getOwnerId());
        verify(accountService).createAccount(1L, AccountType.SAVINGS, Currency.USD, 1000.0);
    }

    @Test
    void createAccountUsesDefaultBalanceOverloadWhenInitialBalanceMissing() {
        CreateAccountRequestDto requestDto = new CreateAccountRequestDto(2L, AccountType.CHECKING, Currency.EUR, null);
        Account expectedAccount = new Account(AccountType.CHECKING, Currency.EUR, 0.0, new User());

        when(accountService.createAccount(2L, AccountType.CHECKING, Currency.EUR)).thenReturn(expectedAccount);

        ResponseEntity<AccountResponseDto> response = accountController.createAccount(requestDto);
        AccountResponseDto body = response.getBody();

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(body);
        assertEquals(AccountType.CHECKING, body.getAccountType());
        assertEquals(Currency.EUR, body.getCurrency());
        assertEquals(0.0, body.getBalance());
        assertNull(body.getOwnerId());
        verify(accountService).createAccount(2L, AccountType.CHECKING, Currency.EUR);
    }

    @Test
    void getAccountByIdReturnsAccountFromService() {
        Account expectedAccount = new Account(AccountType.INVESTMENT, Currency.USD, 500.0, new User());

        when(accountService.getAccountById(3L)).thenReturn(expectedAccount);

        ResponseEntity<AccountResponseDto> response = accountController.getAccountById(3L);
        AccountResponseDto body = response.getBody();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(body);
        assertEquals(AccountType.INVESTMENT, body.getAccountType());
        assertEquals(Currency.USD, body.getCurrency());
        assertEquals(500.0, body.getBalance());
        verify(accountService).getAccountById(3L);
    }

    @Test
    void updateAccountDelegatesToServiceAndReturnsUpdatedAccount() {
        UpdateAccountRequestDto requestDto = new UpdateAccountRequestDto(AccountType.CRYPTO, Currency.INR);
        Account updatedAccount = new Account(AccountType.CRYPTO, Currency.INR, 250.0, new User());

        when(accountService.updateAccount(4L, AccountType.CRYPTO, Currency.INR)).thenReturn(updatedAccount);

        ResponseEntity<AccountResponseDto> response = accountController.updateAccount(4L, requestDto);
        AccountResponseDto body = response.getBody();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(body);
        assertEquals(AccountType.CRYPTO, body.getAccountType());
        assertEquals(Currency.INR, body.getCurrency());
        assertEquals(250.0, body.getBalance());
        verify(accountService).updateAccount(4L, AccountType.CRYPTO, Currency.INR);
    }

    @Test
    void deleteAccountDelegatesToServiceAndReturnsNoContent() {
        ResponseEntity<Void> response = accountController.deleteAccount(5L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(accountService).deleteAccount(5L);
    }

    @Nested
    class CreateAccountTests {

        @Test
        void testCreateAccountWithUSDCurrency() {
            CreateAccountRequestDto requestDto = new CreateAccountRequestDto(1L, AccountType.SAVINGS, Currency.USD, 5000.0);
            Account expectedAccount = new Account(AccountType.SAVINGS, Currency.USD, 5000.0, new User());

            when(accountService.createAccount(1L, AccountType.SAVINGS, Currency.USD, 5000.0))
                    .thenReturn(expectedAccount);

            ResponseEntity<AccountResponseDto> response = accountController.createAccount(requestDto);
            AccountResponseDto body = response.getBody();

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(body);
            assertEquals(Currency.USD, body.getCurrency());
            verify(accountService).createAccount(1L, AccountType.SAVINGS, Currency.USD, 5000.0);
        }

        @Test
        void testCreateAccountWithEURCurrency() {
            CreateAccountRequestDto requestDto = new CreateAccountRequestDto(2L, AccountType.CHECKING, Currency.EUR, 3000.0);
            Account expectedAccount = new Account(AccountType.CHECKING, Currency.EUR, 3000.0, new User());

            when(accountService.createAccount(2L, AccountType.CHECKING, Currency.EUR, 3000.0))
                    .thenReturn(expectedAccount);

            ResponseEntity<AccountResponseDto> response = accountController.createAccount(requestDto);
            AccountResponseDto body = response.getBody();

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(body);
            assertEquals(Currency.EUR, body.getCurrency());
            verify(accountService).createAccount(2L, AccountType.CHECKING, Currency.EUR, 3000.0);
        }

        @Test
        void testCreateAccountWithINRCurrency() {
            CreateAccountRequestDto requestDto = new CreateAccountRequestDto(3L, AccountType.INVESTMENT, Currency.INR, 100000.0);
            Account expectedAccount = new Account(AccountType.INVESTMENT, Currency.INR, 100000.0, new User());

            when(accountService.createAccount(3L, AccountType.INVESTMENT, Currency.INR, 100000.0))
                    .thenReturn(expectedAccount);

            ResponseEntity<AccountResponseDto> response = accountController.createAccount(requestDto);
            AccountResponseDto body = response.getBody();

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(body);
            assertEquals(Currency.INR, body.getCurrency());
        }

        @Test
        void testCreateMultipleAccountsForSameUser() {
            User user = new User();
            
            // Create first account
            CreateAccountRequestDto requestDto1 = new CreateAccountRequestDto(1L, AccountType.SAVINGS, Currency.USD, 5000.0);
            Account account1 = new Account(AccountType.SAVINGS, Currency.USD, 5000.0, user);

            when(accountService.createAccount(1L, AccountType.SAVINGS, Currency.USD, 5000.0))
                    .thenReturn(account1);

            ResponseEntity<AccountResponseDto> response1 = accountController.createAccount(requestDto1);
            assertEquals(HttpStatus.CREATED, response1.getStatusCode());

            // Create second account (different type)
            CreateAccountRequestDto requestDto2 = new CreateAccountRequestDto(1L, AccountType.CHECKING, Currency.EUR, 3000.0);
            Account account2 = new Account(AccountType.CHECKING, Currency.EUR, 3000.0, user);

            when(accountService.createAccount(1L, AccountType.CHECKING, Currency.EUR, 3000.0))
                    .thenReturn(account2);

            ResponseEntity<AccountResponseDto> response2 = accountController.createAccount(requestDto2);
            AccountResponseDto body2 = response2.getBody();
            assertEquals(HttpStatus.CREATED, response2.getStatusCode());
            assertNotNull(body2);
            assertEquals(AccountType.CHECKING, body2.getAccountType());
        }

        @Test
        void testCreateAccountForNonExistentUserThrowsException() {
            CreateAccountRequestDto requestDto = new CreateAccountRequestDto(999L, AccountType.SAVINGS, Currency.USD, 5000.0);

            when(accountService.createAccount(999L, AccountType.SAVINGS, Currency.USD, 5000.0))
                    .thenThrow(new UserNotFoundException("User not found with id: 999"));

            assertThrows(UserNotFoundException.class,
                    () -> accountController.createAccount(requestDto));
        }

        @Test
        void testCreateAccountWithZeroBalance() {
            CreateAccountRequestDto requestDto = new CreateAccountRequestDto(1L, AccountType.CHECKING, Currency.USD, 0.0);
            Account expectedAccount = new Account(AccountType.CHECKING, Currency.USD, 0.0, new User());

            when(accountService.createAccount(1L, AccountType.CHECKING, Currency.USD, 0.0))
                    .thenReturn(expectedAccount);

            ResponseEntity<AccountResponseDto> response = accountController.createAccount(requestDto);
            AccountResponseDto body = response.getBody();

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(body);
            assertEquals(0.0, body.getBalance());
        }
    }

    @Nested
    class UpdateAccountTests {

        @Test
        void testUpdateAccountCurrencyUSD() {
            UpdateAccountRequestDto requestDto = new UpdateAccountRequestDto(AccountType.SAVINGS, Currency.USD);
            Account updatedAccount = new Account(AccountType.SAVINGS, Currency.USD, 5000.0, new User());

            when(accountService.updateAccount(1L, AccountType.SAVINGS, Currency.USD))
                    .thenReturn(updatedAccount);

            ResponseEntity<AccountResponseDto> response = accountController.updateAccount(1L, requestDto);
            AccountResponseDto body = response.getBody();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(body);
            assertEquals(Currency.USD, body.getCurrency());
        }

        @Test
        void testUpdateAccountCurrencyEUR() {
            UpdateAccountRequestDto requestDto = new UpdateAccountRequestDto(AccountType.CHECKING, Currency.EUR);
            Account updatedAccount = new Account(AccountType.CHECKING, Currency.EUR, 3000.0, new User());

            when(accountService.updateAccount(2L, AccountType.CHECKING, Currency.EUR))
                    .thenReturn(updatedAccount);

            ResponseEntity<AccountResponseDto> response = accountController.updateAccount(2L, requestDto);
            AccountResponseDto body = response.getBody();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(body);
            assertEquals(Currency.EUR, body.getCurrency());
        }

        @Test
        void testUpdateAccountCurrencyINR() {
            UpdateAccountRequestDto requestDto = new UpdateAccountRequestDto(AccountType.INVESTMENT, Currency.INR);
            Account updatedAccount = new Account(AccountType.INVESTMENT, Currency.INR, 100000.0, new User());

            when(accountService.updateAccount(3L, AccountType.INVESTMENT, Currency.INR))
                    .thenReturn(updatedAccount);

            ResponseEntity<AccountResponseDto> response = accountController.updateAccount(3L, requestDto);
            AccountResponseDto body = response.getBody();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(body);
            assertEquals(Currency.INR, body.getCurrency());
        }

        @Test
        void testUpdateNonExistentAccountThrowsException() {
            UpdateAccountRequestDto requestDto = new UpdateAccountRequestDto(AccountType.SAVINGS, Currency.USD);

            when(accountService.updateAccount(999L, AccountType.SAVINGS, Currency.USD))
                    .thenThrow(new AccountNotFoundException("Account not found"));

            assertThrows(AccountNotFoundException.class,
                    () -> accountController.updateAccount(999L, requestDto));
        }
    }

    @Nested
    class DeleteAccountTests {

        @Test
        void testDeleteExistingAccount() {
            ResponseEntity<Void> response = accountController.deleteAccount(1L);

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            verify(accountService).deleteAccount(1L);
        }

        @Test
        void testDeleteNonExistentAccountThrowsException() {
            doThrow(new AccountNotFoundException("Account not found"))
                    .when(accountService).deleteAccount(999L);

            assertThrows(AccountNotFoundException.class,
                    () -> accountController.deleteAccount(999L));
        }

        @Test
        void testDeleteAccountRemovesFromUser() {
            ResponseEntity<Void> response = accountController.deleteAccount(2L);

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            verify(accountService).deleteAccount(2L);
        }
    }

    @Nested
    class GetAccountTests {

        @Test
        void testGetAccountWithUSD() {
            Account account = new Account(AccountType.SAVINGS, Currency.USD, 5000.0, new User());

            when(accountService.getAccountById(1L)).thenReturn(account);

            ResponseEntity<AccountResponseDto> response = accountController.getAccountById(1L);
            AccountResponseDto body = response.getBody();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(body);
            assertEquals(Currency.USD, body.getCurrency());
        }

        @Test
        void testGetAccountWithEUR() {
            Account account = new Account(AccountType.CHECKING, Currency.EUR, 3000.0, new User());

            when(accountService.getAccountById(2L)).thenReturn(account);

            ResponseEntity<AccountResponseDto> response = accountController.getAccountById(2L);
            AccountResponseDto body = response.getBody();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(body);
            assertEquals(Currency.EUR, body.getCurrency());
        }

        @Test
        void testGetNonExistentAccountThrowsException() {
            when(accountService.getAccountById(999L))
                    .thenThrow(new AccountNotFoundException("Account not found"));

            assertThrows(AccountNotFoundException.class,
                    () -> accountController.getAccountById(999L));
        }
    }
    
    @Nested
    class AccountTypeTests {

        @Test
        void testCreateSavingsAccount() {
            CreateAccountRequestDto requestDto = new CreateAccountRequestDto(1L, AccountType.SAVINGS, Currency.USD, 5000.0);
            Account account = new Account(AccountType.SAVINGS, Currency.USD, 5000.0, new User());

            when(accountService.createAccount(1L, AccountType.SAVINGS, Currency.USD, 5000.0))
                    .thenReturn(account);

            ResponseEntity<AccountResponseDto> response = accountController.createAccount(requestDto);
            AccountResponseDto body = response.getBody();

            assertNotNull(body);
            assertEquals(AccountType.SAVINGS, body.getAccountType());
        }

        @Test
        void testCreateCheckingAccount() {
            CreateAccountRequestDto requestDto = new CreateAccountRequestDto(1L, AccountType.CHECKING, Currency.USD, 5000.0);
            Account account = new Account(AccountType.CHECKING, Currency.USD, 5000.0, new User());

            when(accountService.createAccount(1L, AccountType.CHECKING, Currency.USD, 5000.0))
                    .thenReturn(account);

            ResponseEntity<AccountResponseDto> response = accountController.createAccount(requestDto);
            AccountResponseDto body = response.getBody();

            assertNotNull(body);
            assertEquals(AccountType.CHECKING, body.getAccountType());
        }

        @Test
        void testCreateInvestmentAccount() {
            CreateAccountRequestDto requestDto = new CreateAccountRequestDto(1L, AccountType.INVESTMENT, Currency.USD, 5000.0);
            Account account = new Account(AccountType.INVESTMENT, Currency.USD, 5000.0, new User());

            when(accountService.createAccount(1L, AccountType.INVESTMENT, Currency.USD, 5000.0))
                    .thenReturn(account);

            ResponseEntity<AccountResponseDto> response = accountController.createAccount(requestDto);
            AccountResponseDto body = response.getBody();

            assertNotNull(body);
            assertEquals(AccountType.INVESTMENT, body.getAccountType());
        }
    }
}


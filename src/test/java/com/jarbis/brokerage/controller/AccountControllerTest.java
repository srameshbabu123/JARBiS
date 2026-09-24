package com.jarbis.brokerage.controller;

import com.jarbis.brokerage.dto.request.CreateAccountRequestDto;
import com.jarbis.brokerage.dto.request.UpdateAccountRequestDto;
import com.jarbis.brokerage.dto.response.AccountResponseDto;
import com.jarbis.brokerage.entity.Account;
import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.enums.AccountType;
import com.jarbis.brokerage.enums.Currency;
import com.jarbis.brokerage.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountControllerTest {

    private final AccountService accountService = mock(AccountService.class);
    private final AccountController accountController = new AccountController(accountService);

    @Test
    void createAccountUsesExplicitInitialBalanceWhenProvided() {
        CreateAccountRequestDto requestDto = new CreateAccountRequestDto(1L, AccountType.SAVINGS, Currency.USD, 1000.0);
        Account expectedAccount = new Account(AccountType.SAVINGS, Currency.USD, 1000.0, new User());

        when(accountService.createAccount(1L, AccountType.SAVINGS, Currency.USD, 1000.0)).thenReturn(expectedAccount);

        ResponseEntity<AccountResponseDto> response = accountController.createAccount(requestDto);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(AccountType.SAVINGS, response.getBody().getAccountType());
        assertEquals(Currency.USD, response.getBody().getCurrency());
        assertEquals(1000.0, response.getBody().getBalance());
        assertNull(response.getBody().getOwnerId());
        verify(accountService).createAccount(1L, AccountType.SAVINGS, Currency.USD, 1000.0);
    }

    @Test
    void createAccountUsesDefaultBalanceOverloadWhenInitialBalanceMissing() {
        CreateAccountRequestDto requestDto = new CreateAccountRequestDto(2L, AccountType.CHECKING, Currency.EUR, null);
        Account expectedAccount = new Account(AccountType.CHECKING, Currency.EUR, 0.0, new User());

        when(accountService.createAccount(2L, AccountType.CHECKING, Currency.EUR)).thenReturn(expectedAccount);

        ResponseEntity<AccountResponseDto> response = accountController.createAccount(requestDto);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(AccountType.CHECKING, response.getBody().getAccountType());
        assertEquals(Currency.EUR, response.getBody().getCurrency());
        assertEquals(0.0, response.getBody().getBalance());
        assertNull(response.getBody().getOwnerId());
        verify(accountService).createAccount(2L, AccountType.CHECKING, Currency.EUR);
    }

    @Test
    void getAccountByIdReturnsAccountFromService() {
        Account expectedAccount = new Account(AccountType.INVESTMENT, Currency.USD, 500.0, new User());

        when(accountService.getAccountById(3L)).thenReturn(expectedAccount);

        ResponseEntity<AccountResponseDto> response = accountController.getAccountById(3L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(AccountType.INVESTMENT, response.getBody().getAccountType());
        assertEquals(Currency.USD, response.getBody().getCurrency());
        assertEquals(500.0, response.getBody().getBalance());
        verify(accountService).getAccountById(3L);
    }

    @Test
    void updateAccountDelegatesToServiceAndReturnsUpdatedAccount() {
        UpdateAccountRequestDto requestDto = new UpdateAccountRequestDto(AccountType.CRYPTO, Currency.INR);
        Account updatedAccount = new Account(AccountType.CRYPTO, Currency.INR, 250.0, new User());

        when(accountService.updateAccount(4L, AccountType.CRYPTO, Currency.INR)).thenReturn(updatedAccount);

        ResponseEntity<AccountResponseDto> response = accountController.updateAccount(4L, requestDto);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(AccountType.CRYPTO, response.getBody().getAccountType());
        assertEquals(Currency.INR, response.getBody().getCurrency());
        assertEquals(250.0, response.getBody().getBalance());
        verify(accountService).updateAccount(4L, AccountType.CRYPTO, Currency.INR);
    }

    @Test
    void deleteAccountDelegatesToServiceAndReturnsNoContent() {
        ResponseEntity<Void> response = accountController.deleteAccount(5L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(accountService).deleteAccount(5L);
    }
}


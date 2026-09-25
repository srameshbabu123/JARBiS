package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.Account;
import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.enums.AccountType;
import com.jarbis.brokerage.enums.Currency;
import com.jarbis.brokerage.exception.AccountClosureException;
import com.jarbis.brokerage.exception.AccountNotFoundException;
import com.jarbis.brokerage.exception.UserNotFoundException;
import com.jarbis.brokerage.repository.AccountRepository;
import com.jarbis.brokerage.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service layer tests for Account management following TDD principles.
 * These tests are aligned with the current AccountService implementation.
 */
class AccountServiceTest {

    private AccountRepository accountRepository;
    private UserRepository userRepository;
    private HoldingService holdingService;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        userRepository = mock(UserRepository.class);
        holdingService = mock(HoldingService.class);
        accountService = new AccountService(accountRepository, userRepository, holdingService);
    }

    @Nested
    class CreateAccountTests {

        @Test
        void createAccountUsesDefaultBalanceWhenNotProvided() {
            User user = userWithId(1L, "john@example.com");
            Account savedAccount = new Account(AccountType.SAVINGS, Currency.USD, 0.0, user);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

            Account result = accountService.createAccount(1L, AccountType.SAVINGS, Currency.USD);

            assertNotNull(result);
            assertEquals(AccountType.SAVINGS, result.getAccountType());
            assertEquals(Currency.USD, result.getCurrency());
            assertEquals(0.0, result.getBalance());
            verify(userRepository).findById(1L);
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        void createAccountUsesZeroWhenExplicitBalanceIsNull() {
            User user = userWithId(2L, "jane@example.com");
            Account savedAccount = new Account(AccountType.CHECKING, Currency.EUR, 0.0, user);

            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

            Account result = accountService.createAccount(2L, AccountType.CHECKING, Currency.EUR, null);

            assertEquals(0.0, result.getBalance());
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        void createAccountUsesExplicitInitialBalance() {
            User user = userWithId(3L, "alice@example.com");
            Account savedAccount = new Account(AccountType.INVESTMENT, Currency.INR, 5000.0, user);

            when(userRepository.findById(3L)).thenReturn(Optional.of(user));
            when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

            Account result = accountService.createAccount(3L, AccountType.INVESTMENT, Currency.INR, 5000.0);

            assertEquals(5000.0, result.getBalance());
            assertEquals(Currency.INR, result.getCurrency());
        }

        @Test
        void createAccountAllowsMultipleAccountsForSameUserAcrossTypesAndCurrencies() {
            User user = userWithId(4L, "multi@example.com");

            when(userRepository.findById(4L)).thenReturn(Optional.of(user));
            when(accountRepository.save(any(Account.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Account savings = accountService.createAccount(4L, AccountType.SAVINGS, Currency.USD, 1000.0);
            Account checking = accountService.createAccount(4L, AccountType.CHECKING, Currency.EUR, 2000.0);
            Account investment = accountService.createAccount(4L, AccountType.INVESTMENT, Currency.INR, 3000.0);

            assertEquals(AccountType.SAVINGS, savings.getAccountType());
            assertEquals(AccountType.CHECKING, checking.getAccountType());
            assertEquals(Currency.INR, investment.getCurrency());
            assertEquals(3, user.getAccounts().size());
            verify(accountRepository, times(3)).save(any(Account.class));
        }

        @Test
        void createAccountForMissingUserThrowsException() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class,
                    () -> accountService.createAccount(999L, AccountType.SAVINGS, Currency.USD));

            verify(accountRepository, never()).save(any(Account.class));
        }
    }

    @Nested
    class GetAccountTests {

        @Test
        void getAccountByIdReturnsAccountWhenFound() {
            Account account = new Account(AccountType.SAVINGS, Currency.USD, 1000.0, userWithId(1L, "john@example.com"));

            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

            Account result = accountService.getAccountById(1L);

            assertNotNull(result);
            assertEquals(AccountType.SAVINGS, result.getAccountType());
            verify(accountRepository).findById(1L);
        }

        @Test
        void getAccountByIdThrowsWhenMissing() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(AccountNotFoundException.class,
                    () -> accountService.getAccountById(999L));
        }
    }

    @Nested
    class UpdateAccountTests {

        @Test
        void updateAccountChangesTypeAndCurrencyWhenProvided() {
            Account account = new Account(AccountType.SAVINGS, Currency.USD, 5000.0, userWithId(2L, "jane@example.com"));

            when(accountRepository.findById(2L)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Account result = accountService.updateAccount(2L, AccountType.CHECKING, Currency.EUR);

            assertEquals(AccountType.CHECKING, result.getAccountType());
            assertEquals(Currency.EUR, result.getCurrency());
            verify(accountRepository).save(account);
        }

        @Test
        void updateAccountPreservesExistingFieldsWhenInputsAreNull() {
            Account account = new Account(AccountType.INVESTMENT, Currency.INR, 4000.0, userWithId(3L, "bob@example.com"));

            when(accountRepository.findById(3L)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Account result = accountService.updateAccount(3L, null, null);

            assertEquals(AccountType.INVESTMENT, result.getAccountType());
            assertEquals(Currency.INR, result.getCurrency());
        }

        @Test
        void updateAccountThrowsWhenAccountMissing() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(AccountNotFoundException.class,
                    () -> accountService.updateAccount(999L, AccountType.SAVINGS, Currency.USD));

            verify(accountRepository, never()).save(any(Account.class));
        }
    }

    @Nested
    class DeleteAccountTests {

        @Test
        void deleteAccountDelegatesToRepository() {
            Account account = new Account(AccountType.SAVINGS, Currency.USD, 1000.0, userWithId(1L, "john@example.com"));

            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

            accountService.deleteAccount(1L);

            verify(accountRepository).delete(account);
        }

        @Test
        void deleteAccountThrowsWhenMissing() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(AccountNotFoundException.class,
                    () -> accountService.deleteAccount(999L));

            verify(accountRepository, never()).delete(any(Account.class));
        }
    }

    @Nested
    class QueryAndClosureTests {

        @Test
        void getAccountsByUserReturnsRepositoryResults() {
            List<Account> accounts = List.of(
                    new Account(AccountType.SAVINGS, Currency.USD, 100.0, userWithId(10L, "owner@example.com")),
                    new Account(AccountType.CHECKING, Currency.EUR, 200.0, userWithId(10L, "owner@example.com"))
            );

            when(userRepository.existsById(10L)).thenReturn(true);
            when(accountRepository.findByOwnerId(10L)).thenReturn(accounts);

            List<Account> result = accountService.getAccountsByUser(10L);

            assertEquals(2, result.size());
            verify(accountRepository).findByOwnerId(10L);
        }

        @Test
        void getAccountsByUserThrowsWhenUserMissing() {
            when(userRepository.existsById(999L)).thenReturn(false);

            assertThrows(UserNotFoundException.class,
                    () -> accountService.getAccountsByUser(999L));
        }

        @Test
        void closeAccountDeletesWhenBalanceIsZeroAndNoHoldings() {
            Account account = new Account(AccountType.SAVINGS, Currency.USD, 0.0, userWithId(11L, "close@example.com"));

            when(accountRepository.findById(11L)).thenReturn(Optional.of(account));

            accountService.closeAccount(11L);

            verify(accountRepository).delete(account);
        }

        @Test
        void closeAccountThrowsWhenBalanceIsNotZero() {
            Account account = new Account(AccountType.SAVINGS, Currency.USD, 50.0, userWithId(12L, "balance@example.com"));

            when(accountRepository.findById(12L)).thenReturn(Optional.of(account));

            assertThrows(AccountClosureException.class,
                    () -> accountService.closeAccount(12L));

            verify(accountRepository, never()).delete(any(Account.class));
        }
    }

    private User userWithId(Long id, String email) {
        User user = new User("Test User", email, "hashed");
        user.setId(id);
        return user;
    }
}


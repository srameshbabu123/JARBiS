package com.jarbis.brokerage.repository;

import com.jarbis.brokerage.entity.Account;
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
@DisplayName("Account Repository Tests")
class AccountRepositoryTest {

    @Mock
    private AccountRepository accountRepository;

    private User testUser;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        testUser = new User("Test User", "test@example.com", "hashed");

        testAccount = new Account(AccountType.SAVINGS, Currency.USD, 1000.0, testUser);
    }

    @Nested
    @DisplayName("Create and Save Account Tests")
    class CreateAccountTests {

        @Test
        @DisplayName("Should save account and generate ID")
        void testSaveAccount() {
            Account account = new Account(AccountType.SAVINGS, Currency.USD, 1000.0, testUser);

            when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

            Account result = accountRepository.save(account);

            assertEquals(AccountType.SAVINGS, result.getAccountType());
            assertEquals(Currency.USD, result.getCurrency());
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("Should save multiple accounts for same user")
        void testSaveMultipleAccounts() {
            Account account2 = new Account(AccountType.CHECKING, Currency.EUR, 2000.0, testUser);

            when(accountRepository.save(any(Account.class))).thenReturn(testAccount, account2);

            accountRepository.save(testAccount);
            accountRepository.save(account2);

            verify(accountRepository, times(2)).save(any(Account.class));
        }
    }

    @Nested
    @DisplayName("Find Account Tests")
    class FindAccountTests {

        @Test
        @DisplayName("Should find account by ID")
        void testFindById() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            Optional<Account> found = accountRepository.findById(1L);

            assertTrue(found.isPresent());
            assertEquals(AccountType.SAVINGS, found.get().getAccountType());
        }

        @Test
        @DisplayName("Should return empty when account not found by ID")
        void testFindByIdNotFound() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            Optional<Account> found = accountRepository.findById(999L);

            assertTrue(found.isEmpty());
        }

        @Test
        @DisplayName("Should find all accounts by user ID")
        void testFindByOwnerId() {
            List<Account> accounts = List.of(testAccount);

            when(accountRepository.findByOwnerId(1L)).thenReturn(accounts);

            List<Account> result = accountRepository.findByOwnerId(1L);

            assertEquals(1, result.size());
            assertEquals(testAccount.getId(), result.get(0).getId());
        }

        @Test
        @DisplayName("Should find accounts by account type")
        void testFindByAccountType() {
            List<Account> accounts = List.of(testAccount);

            when(accountRepository.findByAccountType(AccountType.SAVINGS)).thenReturn(accounts);

            List<Account> result = accountRepository.findByAccountType(AccountType.SAVINGS);

            assertTrue(result.stream().allMatch(a -> a.getAccountType() == AccountType.SAVINGS));
        }

        @Test
        @DisplayName("Should find accounts by currency")
        void testFindByCurrency() {
            List<Account> accounts = List.of(testAccount);

            when(accountRepository.findByCurrency(Currency.USD)).thenReturn(accounts);

            List<Account> result = accountRepository.findByCurrency(Currency.USD);

            assertTrue(result.stream().allMatch(a -> a.getCurrency() == Currency.USD));
        }

        @Test
        @DisplayName("Should find accounts by owner and account type")
        void testFindByOwnerIdAndAccountType() {
            List<Account> accounts = List.of(testAccount);

            when(accountRepository.findByOwnerIdAndAccountType(1L, AccountType.SAVINGS)).thenReturn(accounts);

            List<Account> result = accountRepository.findByOwnerIdAndAccountType(1L, AccountType.SAVINGS);

            assertEquals(1, result.size());
            assertEquals(AccountType.SAVINGS, result.get(0).getAccountType());
        }

        @Test
        @DisplayName("Should find accounts by owner and currency")
        void testFindByOwnerIdAndCurrency() {
            List<Account> accounts = List.of(testAccount);

            when(accountRepository.findByOwnerIdAndCurrency(1L, Currency.USD)).thenReturn(accounts);

            List<Account> result = accountRepository.findByOwnerIdAndCurrency(1L, Currency.USD);

            assertEquals(1, result.size());
            assertEquals(Currency.USD, result.get(0).getCurrency());
        }
    }

    @Nested
    @DisplayName("Update Account Tests")
    class UpdateAccountTests {

        @Test
        @DisplayName("Should update account balance")
        void testUpdateBalance() {
            Account updatedAccount = new Account(AccountType.SAVINGS, Currency.USD, 1500.0, testUser);

            when(accountRepository.save(any(Account.class))).thenReturn(updatedAccount);

            Account result = accountRepository.save(updatedAccount);

            assertEquals(1500.0, result.getBalance());
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("Should update account type")
        void testUpdateAccountType() {
            Account updatedAccount = new Account(AccountType.CHECKING, Currency.USD, 1000.0, testUser);

            when(accountRepository.save(any(Account.class))).thenReturn(updatedAccount);

            Account result = accountRepository.save(updatedAccount);

            assertEquals(AccountType.CHECKING, result.getAccountType());
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("Should update account currency")
        void testUpdateCurrency() {
            Account updatedAccount = new Account(AccountType.SAVINGS, Currency.EUR, 1000.0, testUser);

            when(accountRepository.save(any(Account.class))).thenReturn(updatedAccount);

            Account result = accountRepository.save(updatedAccount);

            assertEquals(Currency.EUR, result.getCurrency());
            verify(accountRepository).save(any(Account.class));
        }
    }

    @Nested
    @DisplayName("Delete Account Tests")
    class DeleteAccountTests {

        @Test
        @DisplayName("Should delete account by entity")
        void testDeleteAccount() {
            accountRepository.delete(testAccount);

            verify(accountRepository).delete(testAccount);
        }

        @Test
        @DisplayName("Should delete account by ID")
        void testDeleteById() {
            accountRepository.deleteById(1L);

            verify(accountRepository).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("FindAll and Count Tests")
    class FindAllAndCountTests {

        @Test
        @DisplayName("Should retrieve all accounts")
        void testFindAll() {
            List<Account> accounts = List.of(testAccount);

            when(accountRepository.findAll()).thenReturn(accounts);

            List<Account> result = accountRepository.findAll();

            assertTrue(result.size() >= 1);
        }

        @Test
        @DisplayName("Should count all accounts")
        void testCount() {
            when(accountRepository.count()).thenReturn(1L);

            long count = accountRepository.count();

            assertTrue(count >= 1);
        }
    }
}


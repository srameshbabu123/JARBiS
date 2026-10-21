package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.Account;
import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.enums.AccountType;
import com.jarbis.brokerage.enums.Currency;
import com.jarbis.brokerage.repository.AccountRepository;
import com.jarbis.brokerage.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FreshAccountServiceTest {
    @Test void registrationSavesUserAndZeroBalanceAccountTogether() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        String password = UUID.randomUUID().toString();
        when(encoder.encode(password)).thenReturn("encoded-test-value");
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        User user = new UserService(users, encoder).createUser("Fresh User", "fresh@example.test", password);
        assertEquals(1, user.getAccounts().size());
        Account account = user.getAccounts().get(0);
        assertSame(user, account.getOwner());
        assertEquals(0.0, account.getBalance());
        assertEquals(AccountType.INVESTMENT, account.getAccountType());
        assertEquals(Currency.USD, account.getCurrency());
        assertTrue(account.getHoldings().isEmpty());
        assertTrue(account.getOrders().isEmpty());
        verify(users, times(1)).save(user);
    }

    @Test void missingAccountRepairUsesAuthenticatedUserAndReusesExistingAccount() {
        UserRepository users = mock(UserRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        User user = new User(); user.setId(100000027L);
        when(users.findForAccountInitialization(user.getId())).thenReturn(Optional.of(user));
        when(accounts.findByOwnerId(user.getId())).thenReturn(List.of());
        when(accounts.save(any(Account.class))).thenAnswer(call -> call.getArgument(0));
        AccountService service = new AccountService(accounts, users, mock(HoldingService.class));
        Account created = service.initializeMyAccount(user.getId());
        assertEquals(0.0, created.getBalance());
        assertSame(user, created.getOwner());
        when(accounts.findByOwnerId(user.getId())).thenReturn(List.of(created));
        assertSame(created, service.initializeMyAccount(user.getId()));
        verify(accounts, times(1)).save(any(Account.class));
        verify(users, times(2)).findForAccountInitialization(user.getId());
    }
}

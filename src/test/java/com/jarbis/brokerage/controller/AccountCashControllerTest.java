package com.jarbis.brokerage.controller;

import com.jarbis.brokerage.dto.request.CashAmountRequestDto;
import com.jarbis.brokerage.entity.Account;
import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.enums.AccountType;
import com.jarbis.brokerage.enums.Currency;
import com.jarbis.brokerage.service.AccountService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountCashControllerTest {
    private final AccountService service=mock(AccountService.class);
    private final AccountController controller=new AccountController(service);
    private final Account account=new Account(AccountType.INVESTMENT,Currency.USD,125.0,new User());
    @Test void discoveryUsesAuthenticatedUser(){when(service.getAccountsByUser(9L)).thenReturn(List.of(account));assertEquals(1,controller.getMyAccounts(9L).getBody().size());verify(service).getAccountsByUser(9L);}
    @Test void accountReadUsesOwnershipCheck(){when(service.getAccountByUser(9L,42L)).thenReturn(account);assertEquals(125.0,controller.getMyAccount(9L,42L).getBody().getBalance());verify(service).getAccountByUser(9L,42L);}
    @Test void cashEndpointsPassSessionUserAndAmount(){
        when(service.changeCash(9L,42L,25.0,false)).thenReturn(account);
        when(service.changeCash(9L,42L,25.0,true)).thenReturn(account);
        assertEquals(125.0,controller.deposit(9L,42L,new CashAmountRequestDto(25.0)).getBody().getBalance());
        assertEquals(125.0,controller.withdraw(9L,42L,new CashAmountRequestDto(25.0)).getBody().getBalance());
        verify(service).changeCash(9L,42L,25.0,false);verify(service).changeCash(9L,42L,25.0,true);
    }
}

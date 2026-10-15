package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.Account;
import com.jarbis.brokerage.entity.Order;
import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.enums.AccountType;
import com.jarbis.brokerage.enums.Currency;
import com.jarbis.brokerage.enums.OrderSide;
import com.jarbis.brokerage.enums.OrderStatus;
import com.jarbis.brokerage.exception.AccountNotFoundException;
import com.jarbis.brokerage.exception.InsufficientBalanceException;
import com.jarbis.brokerage.exception.InvalidAmountException;
import com.jarbis.brokerage.repository.AccountRepository;
import com.jarbis.brokerage.repository.UserRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountCashTest {
    private final AccountRepository repository=mock(AccountRepository.class);
    private final AccountService service=new AccountService(repository,mock(UserRepository.class),mock(HoldingService.class));
    private Account account(double balance){
        Account account=new Account(AccountType.INVESTMENT,Currency.USD,balance,new User());
        when(repository.findOwnedForCashUpdate(9L,42L)).thenReturn(Optional.of(account));
        when(repository.save(account)).thenReturn(account);
        return account;
    }
    @Test void depositUpdatesCash(){Account a=account(100);assertEquals(125.25,service.changeCash(9L,42L,25.25,false).getBalance());verify(repository).save(a);}
    @Test void withdrawUpdatesCash(){account(100);assertEquals(74.75,service.changeCash(9L,42L,25.25,true).getBalance());}
    @Test void invalidAmountsNeverReadOrWriteAccounts(){
        for(Double amount:new Double[]{null,0.0,-1.0,Double.NaN,Double.POSITIVE_INFINITY,1.001})assertThrows(InvalidAmountException.class,()->service.changeCash(9L,42L,amount,false));
        verifyNoInteractions(repository);
    }
    @Test void otherUsersAccountIsNotAccessible(){when(repository.findOwnedForCashUpdate(8L,42L)).thenReturn(Optional.empty());assertThrows(AccountNotFoundException.class,()->service.changeCash(8L,42L,25.0,false));verify(repository,never()).save(any());}
    @Test void insufficientFundsDoNotChangeBalance(){Account a=account(100);assertThrows(InsufficientBalanceException.class,()->service.changeCash(9L,42L,101.0,true));assertEquals(100.0,a.getBalance());verify(repository,never()).save(any());}
    @Test void openBuyOrdersReserveRemainingCash(){
        Account a=account(100);Order order=new Order();order.setSide(OrderSide.BUY);order.setStatus(OrderStatus.PARTIALLY_FILLED);order.setQuantity(10.0);order.setRemainingQuantity(3.0);order.setPrice(20.0);a.addOrder(order);
        assertThrows(InsufficientBalanceException.class,()->service.changeCash(9L,42L,40.01,true));assertEquals(60.0,service.changeCash(9L,42L,40.0,true).getBalance());
    }
    @Test void cancelledOrdersDoNotReserveCash(){Account a=account(100);Order order=new Order();order.setSide(OrderSide.BUY);order.setStatus(OrderStatus.CANCELLED);order.setQuantity(100.0);order.setPrice(20.0);a.addOrder(order);assertEquals(0.0,service.changeCash(9L,42L,100.0,true).getBalance());}
}

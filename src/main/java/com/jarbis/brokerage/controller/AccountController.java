package com.jarbis.brokerage.controller;

import com.jarbis.brokerage.dto.request.CreateAccountRequestDto;
import com.jarbis.brokerage.dto.request.UpdateAccountRequestDto;
import com.jarbis.brokerage.dto.response.AccountResponseDto;
import com.jarbis.brokerage.entity.Account;
import com.jarbis.brokerage.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

        @PostMapping
        public ResponseEntity<AccountResponseDto> createAccount(@RequestBody CreateAccountRequestDto requestDto) {
            Account account = requestDto.getInitialBalance() == null
                    ? accountService.createAccount(requestDto.getUserId(), requestDto.getAccountType(), requestDto.getCurrency())
                    : accountService.createAccount(
                    requestDto.getUserId(),
                    requestDto.getAccountType(),
                    requestDto.getCurrency(),
                    requestDto.getInitialBalance());

        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponseDto.fromAccount(account));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponseDto> getAccountById(@PathVariable Long accountId) {
        return ResponseEntity.ok(AccountResponseDto.fromAccount(accountService.getAccountById(accountId)));
    }

    @PutMapping("/{accountId}")
    public ResponseEntity<AccountResponseDto> updateAccount(
            @PathVariable Long accountId,
            @RequestBody UpdateAccountRequestDto requestDto) {
        Account account = accountService.updateAccount(
                accountId,
                requestDto.getAccountType(),
                requestDto.getCurrency());

        return ResponseEntity.ok(AccountResponseDto.fromAccount(account));
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long accountId) {
        accountService.deleteAccount(accountId);
        return ResponseEntity.noContent().build();
    }
}

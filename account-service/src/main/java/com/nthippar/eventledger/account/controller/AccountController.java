package com.nthippar.eventledger.account.controller;

import com.nthippar.eventledger.account.api.AccountDetailsResponse;
import com.nthippar.eventledger.account.api.ApplyTransactionRequest;
import com.nthippar.eventledger.account.api.BalanceResponse;
import com.nthippar.eventledger.account.api.TransactionResponse;
import com.nthippar.eventledger.account.service.AccountService;
import com.nthippar.eventledger.account.service.TransactionApplicationResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> applyTransaction(
            @PathVariable String accountId,
            @Valid @RequestBody ApplyTransactionRequest request
    ) {
        TransactionApplicationResult result =
                service.applyTransaction(accountId, request);

        if (result.created()) {
            return ResponseEntity
                    .created(URI.create(
                            "/accounts/"
                                    + accountId
                                    + "/transactions/"
                                    + result.transaction().eventId()
                    ))
                    .body(result.transaction());
        }

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(result.transaction());
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable String accountId
    ) {
        return ResponseEntity.ok(service.getBalance(accountId));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountDetailsResponse> getAccountDetails(
            @PathVariable String accountId
    ) {
        return ResponseEntity.ok(service.getAccountDetails(accountId));
    }
}
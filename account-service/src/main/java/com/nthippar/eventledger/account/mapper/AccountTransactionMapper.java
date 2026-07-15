package com.nthippar.eventledger.account.mapper;

import com.nthippar.eventledger.account.api.ApplyTransactionRequest;
import com.nthippar.eventledger.account.api.TransactionResponse;
import com.nthippar.eventledger.account.domain.AccountTransaction;
import org.springframework.stereotype.Component;

@Component
public class AccountTransactionMapper {

    public AccountTransaction toEntity(
            String accountId,
            ApplyTransactionRequest request
    ) {
        return new AccountTransaction(
                request.eventId(),
                accountId,
                request.type(),
                request.amount(),
                request.currency().toUpperCase(),
                request.eventTimestamp()
        );
    }

    public TransactionResponse toResponse(AccountTransaction transaction) {
        return new TransactionResponse(
                transaction.getEventId(),
                transaction.getAccountId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getEventTimestamp()
        );
    }
}
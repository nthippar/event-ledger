package com.nthippar.eventledger.account.service;

import com.nthippar.eventledger.account.api.TransactionResponse;

public record TransactionApplicationResult(
        TransactionResponse transaction,
        boolean created
) {
}
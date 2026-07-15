package com.nthippar.eventledger.account.api;

import com.nthippar.eventledger.account.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record ApplyTransactionRequest(

        @NotBlank(message = "eventId is required")
        String eventId,

        @NotNull(message = "type is required")
        TransactionType type,

        @NotNull(message = "amount is required")
        @DecimalMin(
                value = "0.01",
                message = "amount must be greater than 0"
        )
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Size(
                min = 3,
                max = 3,
                message = "currency must contain exactly 3 characters"
        )
        String currency,

        @NotNull(message = "eventTimestamp is required")
        Instant eventTimestamp
) {
}
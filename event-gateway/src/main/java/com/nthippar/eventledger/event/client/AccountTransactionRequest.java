package com.nthippar.eventledger.event.client;

import com.nthippar.eventledger.event.domain.EventType;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountTransactionRequest(
        String eventId,
        EventType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp
) {
}
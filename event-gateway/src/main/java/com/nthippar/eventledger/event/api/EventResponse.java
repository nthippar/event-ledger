package com.nthippar.eventledger.event.api;

import com.nthippar.eventledger.event.domain.EventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record EventResponse(
        String eventId,
        String accountId,
        EventType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        Map<String, Object> metadata
) {
}
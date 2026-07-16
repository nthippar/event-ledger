package com.nthippar.eventledger.event.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerEventTest {

    @Test
    void shouldMoveThroughProcessingStates() {
        LedgerEvent event = new LedgerEvent(
                "evt-001",
                "acct-123",
                EventType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z"),
                null
        );

        assertThat(event.getProcessingStatus())
                .isEqualTo(EventProcessingStatus.PENDING);

        event.markApplied();

        assertThat(event.getProcessingStatus())
                .isEqualTo(EventProcessingStatus.APPLIED);

        event.markFailed();

        assertThat(event.getProcessingStatus())
                .isEqualTo(EventProcessingStatus.FAILED);
    }
}
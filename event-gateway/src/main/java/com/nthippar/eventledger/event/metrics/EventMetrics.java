package com.nthippar.eventledger.event.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class EventMetrics {

    private static final String METRIC_NAME =
            "event.ledger.events.submitted";

    private final Counter createdCounter;
    private final Counter duplicateCounter;
    private final Counter failedCounter;

    public EventMetrics(MeterRegistry meterRegistry) {
        this.createdCounter = Counter.builder(METRIC_NAME)
                .description("Number of event submissions handled by result")
                .tag("result", "created")
                .register(meterRegistry);

        this.duplicateCounter = Counter.builder(METRIC_NAME)
                .description("Number of event submissions handled by result")
                .tag("result", "duplicate")
                .register(meterRegistry);

        this.failedCounter = Counter.builder(METRIC_NAME)
                .description("Number of event submissions handled by result")
                .tag("result", "failed")
                .register(meterRegistry);
    }

    public void recordCreated() {
        createdCounter.increment();
    }

    public void recordDuplicate() {
        duplicateCounter.increment();
    }

    public void recordFailed() {
        failedCounter.increment();
    }
}
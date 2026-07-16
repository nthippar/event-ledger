package com.nthippar.eventledger.event.health;

import com.nthippar.eventledger.event.client.AccountServiceClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class AccountServiceCircuitBreakerHealthIndicator
        implements HealthIndicator {

    private final AccountServiceClient accountServiceClient;

    public AccountServiceCircuitBreakerHealthIndicator(
            AccountServiceClient accountServiceClient
    ) {
        this.accountServiceClient = accountServiceClient;
    }

    @Override
    public Health health() {
        CircuitBreaker.State state =
                accountServiceClient.getCircuitBreaker().getState();

        Health.Builder builder =
                state == CircuitBreaker.State.OPEN
                        ? Health.down()
                        : Health.up();

        return builder
                .withDetail("circuitBreaker", "accountService")
                .withDetail("state", state.name())
                .build();
    }
}
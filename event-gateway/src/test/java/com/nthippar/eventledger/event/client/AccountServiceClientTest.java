package com.nthippar.eventledger.event.client;

import com.nthippar.eventledger.event.domain.EventType;
import com.nthippar.eventledger.event.domain.LedgerEvent;
import com.nthippar.eventledger.event.error.AccountServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class AccountServiceClientTest {

    private MockRestServiceServer mockServer;
    private AccountServiceClient client;
    private LedgerEvent event;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();

        mockServer = MockRestServiceServer
                .bindTo(builder)
                .build();

        client = new AccountServiceClient(
                builder,
                "http://account-service"
        );

        event = new LedgerEvent(
                "evt-circuit-breaker",
                "acct-123",
                EventType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z"),
                null
        );
    }

    @Test
    void shouldOpenCircuitAfterRepeatedAccountServiceFailures() {
        mockServer.expect(ExpectedCount.times(3),
                        requestTo(
                                "http://account-service"
                                        + "/accounts/acct-123/transactions"
                        ))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(
                    () -> client.applyTransaction(event)
            )
                    .isInstanceOf(
                            AccountServiceUnavailableException.class
                    )
                    .hasMessage(
                            "Account Service is currently unavailable"
                    );
        }

        assertThat(client.getCircuitBreaker().getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(
                () -> client.applyTransaction(event)
        )
                .isInstanceOf(
                        AccountServiceUnavailableException.class
                )
                .hasMessage(
                        "Account Service circuit breaker is open"
                );

        mockServer.verify();
    }
}
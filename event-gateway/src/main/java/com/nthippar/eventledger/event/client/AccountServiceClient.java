package com.nthippar.eventledger.event.client;

import com.nthippar.eventledger.event.domain.LedgerEvent;
import com.nthippar.eventledger.event.error.AccountServiceUnavailableException;
import com.nthippar.eventledger.event.tracing.TraceConstants;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class AccountServiceClient {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public AccountServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${account-service.base-url}") String baseUrl
    ) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();

        CircuitBreakerConfig circuitBreakerConfig =
                CircuitBreakerConfig.custom()
                        .slidingWindowType(
                                CircuitBreakerConfig.SlidingWindowType.COUNT_BASED
                        )
                        .slidingWindowSize(5)
                        .minimumNumberOfCalls(3)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(15))
                        .permittedNumberOfCallsInHalfOpenState(1)
                        .build();

        this.circuitBreaker = CircuitBreaker.of(
                "accountService",
                circuitBreakerConfig
        );
    }

    public AccountTransactionResponse applyTransaction(
            LedgerEvent event
    ) {
        AccountTransactionRequest request =
                new AccountTransactionRequest(
                        event.getEventId(),
                        event.getType(),
                        event.getAmount(),
                        event.getCurrency(),
                        event.getEventTimestamp()
                );

        Supplier<AccountTransactionResponse> protectedCall =
                CircuitBreaker.decorateSupplier(
                        circuitBreaker,
                        () -> invokeAccountService(event, request)
                );

        try {
            return protectedCall.get();
        } catch (CallNotPermittedException exception) {
            throw new AccountServiceUnavailableException(
                    "Account Service circuit breaker is open",
                    exception
            );
        } catch (RestClientException exception) {
            throw new AccountServiceUnavailableException(
                    "Account Service is currently unavailable",
                    exception
            );
        }
    }

    private AccountTransactionResponse invokeAccountService(
            LedgerEvent event,
            AccountTransactionRequest request
    ) {
        String traceId = MDC.get(TraceConstants.MDC_TRACE_ID);

        RestClient.RequestBodySpec requestSpec = restClient.post()
                .uri(
                        "/accounts/{accountId}/transactions",
                        event.getAccountId()
                )
                .contentType(MediaType.APPLICATION_JSON);

        if (traceId != null && !traceId.isBlank()) {
            requestSpec.header(
                    TraceConstants.TRACE_HEADER,
                    traceId
            );
        }

        return requestSpec
                .body(request)
                .retrieve()
                .body(AccountTransactionResponse.class);
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }
}
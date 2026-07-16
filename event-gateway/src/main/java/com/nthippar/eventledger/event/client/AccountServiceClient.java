package com.nthippar.eventledger.event.client;

import com.nthippar.eventledger.event.domain.LedgerEvent;
import com.nthippar.eventledger.event.error.AccountServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AccountServiceClient {

    private final RestClient restClient;

    public AccountServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${account-service.base-url}") String baseUrl
    ) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();
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

        try {
            return restClient.post()
                    .uri(
                            "/accounts/{accountId}/transactions",
                            event.getAccountId()
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AccountTransactionResponse.class);
        } catch (RestClientException exception) {
            throw new AccountServiceUnavailableException(
                    "Account Service is currently unavailable",
                    exception
            );
        }
    }
}
package com.nthippar.eventledger.account.controller;

import com.nthippar.eventledger.account.repository.AccountTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private AccountTransactionRepository repository;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void shouldApplyCreditTransaction() throws Exception {
        HttpResponse<String> response = postTransaction(
                "acct-123",
                """
                {
                  "eventId": "evt-credit-001",
                  "type": "CREDIT",
                  "amount": 150.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """
        );

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body())
                .contains("\"eventId\":\"evt-credit-001\"");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void shouldNotApplyDuplicateTransactionTwice() throws Exception {
        String body = """
                {
                  "eventId": "evt-duplicate",
                  "type": "CREDIT",
                  "amount": 150.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

        HttpResponse<String> first =
                postTransaction("acct-123", body);

        HttpResponse<String> duplicate =
                postTransaction("acct-123", body);

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(duplicate.statusCode()).isEqualTo(200);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void shouldCalculateBalanceFromCreditsAndDebits() throws Exception {
        postTransaction(
                "acct-123",
                """
                {
                  "eventId": "evt-credit",
                  "type": "CREDIT",
                  "amount": 150.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:00:00Z"
                }
                """
        );

        postTransaction(
                "acct-123",
                """
                {
                  "eventId": "evt-debit",
                  "type": "DEBIT",
                  "amount": 40.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T15:00:00Z"
                }
                """
        );

        HttpResponse<String> response = get(
                "/accounts/acct-123/balance"
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"balance\":110.00");
    }

    @Test
    void shouldCalculateCorrectBalanceForOutOfOrderEvents()
            throws Exception {

        postTransaction(
                "acct-order",
                """
                {
                  "eventId": "evt-later",
                  "type": "CREDIT",
                  "amount": 100.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T16:00:00Z"
                }
                """
        );

        postTransaction(
                "acct-order",
                """
                {
                  "eventId": "evt-earlier",
                  "type": "DEBIT",
                  "amount": 25.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T12:00:00Z"
                }
                """
        );

        HttpResponse<String> response = get(
                "/accounts/acct-order/balance"
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"balance\":75.00");
    }

    @Test
    void shouldReturnRecentTransactionsNewestFirst()
            throws Exception {

        postTransaction(
                "acct-history",
                """
                {
                  "eventId": "evt-late",
                  "type": "CREDIT",
                  "amount": 50.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T15:00:00Z"
                }
                """
        );

        postTransaction(
                "acct-history",
                """
                {
                  "eventId": "evt-early",
                  "type": "DEBIT",
                  "amount": 10.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T13:00:00Z"
                }
                """
        );

        HttpResponse<String> response = get(
                "/accounts/acct-history"
        );

        assertThat(response.statusCode()).isEqualTo(200);

        int latePosition = response.body().indexOf("evt-late");
        int earlyPosition = response.body().indexOf("evt-early");

        assertThat(latePosition).isGreaterThanOrEqualTo(0);
        assertThat(earlyPosition).isGreaterThan(latePosition);
    }

    @Test
    void shouldRejectZeroAmount() throws Exception {
        HttpResponse<String> response = postTransaction(
                "acct-123",
                """
                {
                  "eventId": "evt-invalid",
                  "type": "CREDIT",
                  "amount": 0,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """
        );

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body())
                .contains("\"amount\":\"amount must be greater than 0\"");
        assertThat(repository.count()).isZero();
    }

    @Test
    void shouldRejectConflictingDuplicateTransaction() throws Exception {
        postTransaction(
                "acct-123",
                """
                {
                  "eventId": "evt-conflict",
                  "type": "CREDIT",
                  "amount": 150.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """
        );

        HttpResponse<String> response = postTransaction(
                "acct-123",
                """
                {
                  "eventId": "evt-conflict",
                  "type": "DEBIT",
                  "amount": 75.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T15:00:00Z"
                }
                """
        );

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body())
                .contains("A different transaction already exists");
        assertThat(repository.count()).isEqualTo(1);
    }

    private HttpResponse<String> postTransaction(
            String accountId,
            String body
    ) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:"
                                + port
                                + "/accounts/"
                                + accountId
                                + "/transactions"
                ))
                .header(
                        "Content-Type",
                        MediaType.APPLICATION_JSON_VALUE
                )
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + port + path
                ))
                .GET()
                .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }
}
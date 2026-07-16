package com.nthippar.eventledger.event.controller;

import com.nthippar.eventledger.event.client.AccountServiceClient;
import com.nthippar.eventledger.event.domain.EventProcessingStatus;
import com.nthippar.eventledger.event.error.AccountServiceUnavailableException;
import com.nthippar.eventledger.event.repository.LedgerEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EventControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private LedgerEventRepository repository;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @MockitoBean
    private AccountServiceClient accountServiceClient;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();

        when(accountServiceClient.applyTransaction(any()))
                .thenReturn(null);
    }

    @Test
    void shouldCreateNewEvent() throws Exception {
        HttpResponse<String> response = sendEvent("""
                {
                  "eventId": "evt-001",
                  "accountId": "acct-123",
                  "type": "CREDIT",
                  "amount": 150.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z",
                  "metadata": {
                    "source": "mainframe-batch",
                    "batchId": "B-9042"
                  }
                }
                """);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers()
                .firstValue("Location"))
                .contains("/events/evt-001");
        assertThat(response.body())
                .contains("\"eventId\":\"evt-001\"");
    }

    @Test
    void shouldReturnExistingEventForDuplicateSubmission() throws Exception {
        String requestBody = """
                {
                  "eventId": "evt-duplicate",
                  "accountId": "acct-123",
                  "type": "CREDIT",
                  "amount": 150.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

        HttpResponse<String> firstResponse = sendEvent(requestBody);
        HttpResponse<String> duplicateResponse = sendEvent(requestBody);

        assertThat(firstResponse.statusCode()).isEqualTo(201);
        assertThat(duplicateResponse.statusCode()).isEqualTo(200);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(duplicateResponse.body())
                .contains("\"eventId\":\"evt-duplicate\"");
    }

    @Test
    void shouldRejectZeroAmount() throws Exception {
        HttpResponse<String> response = sendEvent("""
            {
              "eventId": "evt-invalid",
              "accountId": "acct-123",
              "type": "CREDIT",
              "amount": 0,
              "currency": "USD",
              "eventTimestamp": "2026-05-15T14:02:11Z"
            }
            """);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body())
                .contains("\"message\":\"Request validation failed\"");
        assertThat(response.body())
                .contains("\"amount\":\"amount must be greater than 0\"");
        assertThat(repository.count()).isZero();
    }

    @Test
    void shouldRejectMissingRequiredFields() throws Exception {
        HttpResponse<String> response = sendEvent("""
            {
              "currency": "USD"
            }
            """);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body())
                .contains("\"eventId\":\"eventId is required\"");
        assertThat(response.body())
                .contains("\"accountId\":\"accountId is required\"");
        assertThat(response.body())
                .contains("\"type\":\"type is required\"");
        assertThat(response.body())
                .contains("\"amount\":\"amount is required\"");
        assertThat(response.body())
                .contains("\"eventTimestamp\":\"eventTimestamp is required\"");
        assertThat(repository.count()).isZero();
    }

    @Test
    void shouldRetrieveEventById() throws Exception {
        sendEvent("""
            {
              "eventId": "evt-read",
              "accountId": "acct-123",
              "type": "CREDIT",
              "amount": 75.00,
              "currency": "USD",
              "eventTimestamp": "2026-05-15T14:02:11Z"
            }
            """);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + port + "/events/evt-read"
                ))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"eventId\":\"evt-read\"");
    }

    @Test
    void shouldReturn404WhenEventDoesNotExist() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + port + "/events/missing-event"
                ))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body())
                .contains("\"message\":\"Event with ID missing-event not found.\"");
    }

    @Test
    void shouldReturnAccountEventsInChronologicalOrder() throws Exception {
        sendEvent("""
            {
              "eventId": "evt-late",
              "accountId": "acct-order",
              "type": "CREDIT",
              "amount": 50.00,
              "currency": "USD",
              "eventTimestamp": "2026-05-15T15:00:00Z"
            }
            """);

        sendEvent("""
            {
              "eventId": "evt-early",
              "accountId": "acct-order",
              "type": "DEBIT",
              "amount": 10.00,
              "currency": "USD",
              "eventTimestamp": "2026-05-15T14:00:00Z"
            }
            """);

        sendEvent("""
            {
              "eventId": "evt-other",
              "accountId": "acct-other",
              "type": "CREDIT",
              "amount": 25.00,
              "currency": "USD",
              "eventTimestamp": "2026-05-15T13:00:00Z"
            }
            """);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + port
                                + "/events?account=acct-order"
                ))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);

        int earlyPosition = response.body().indexOf("evt-early");
        int latePosition = response.body().indexOf("evt-late");

        assertThat(earlyPosition).isGreaterThanOrEqualTo(0);
        assertThat(latePosition).isGreaterThan(earlyPosition);
        assertThat(response.body()).doesNotContain("evt-other");
    }

    @Test
    void shouldRejectMissingAccountQueryParameter() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + port + "/events"
                ))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void shouldReturn503AndKeepEventWhenAccountServiceIsUnavailable()
            throws Exception {

        when(accountServiceClient.applyTransaction(any()))
                .thenThrow(new AccountServiceUnavailableException(
                        "Account Service is currently unavailable",
                        new RuntimeException("Connection refused")
                ));

        HttpResponse<String> response = sendEvent("""
            {
              "eventId": "evt-account-down",
              "accountId": "acct-123",
              "type": "CREDIT",
              "amount": 150.00,
              "currency": "USD",
              "eventTimestamp": "2026-05-15T14:02:11Z"
            }
            """);

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body())
                .contains("\"message\":\"Account Service is currently unavailable\"");

        assertThat(repository.count()).isEqualTo(1);

        var storedEvent = repository.findById("evt-account-down")
                .orElseThrow();

        assertThat(storedEvent.getProcessingStatus())
                .isEqualTo(EventProcessingStatus.FAILED);
    }

    @Test
    void shouldKeepFailedEventReadableFromGateway() throws Exception {
        when(accountServiceClient.applyTransaction(any()))
                .thenThrow(new AccountServiceUnavailableException(
                        "Account Service is currently unavailable",
                        new RuntimeException("Connection refused")
                ));

        sendEvent("""
            {
              "eventId": "evt-readable-failure",
              "accountId": "acct-123",
              "type": "CREDIT",
              "amount": 50.00,
              "currency": "USD",
              "eventTimestamp": "2026-05-15T14:02:11Z"
            }
            """);

        HttpResponse<String> response = get(
                "/events/evt-readable-failure"
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"eventId\":\"evt-readable-failure\"");
    }

    @Test
    void shouldRetryFailedEventOnDuplicateSubmission() throws Exception {
        when(accountServiceClient.applyTransaction(any()))
                .thenThrow(new AccountServiceUnavailableException(
                        "Account Service is currently unavailable",
                        new RuntimeException("Connection refused")
                ))
                .thenReturn(null);

        String requestBody = """
            {
              "eventId": "evt-retry",
              "accountId": "acct-123",
              "type": "CREDIT",
              "amount": 150.00,
              "currency": "USD",
              "eventTimestamp": "2026-05-15T14:02:11Z"
            }
            """;

        HttpResponse<String> failedResponse = sendEvent(requestBody);
        HttpResponse<String> retryResponse = sendEvent(requestBody);

        assertThat(failedResponse.statusCode()).isEqualTo(503);
        assertThat(retryResponse.statusCode()).isEqualTo(200);
        assertThat(repository.count()).isEqualTo(1);

        var storedEvent = repository.findById("evt-retry")
                .orElseThrow();

        assertThat(storedEvent.getProcessingStatus())
                .isEqualTo(EventProcessingStatus.APPLIED);

        verify(accountServiceClient, times(2))
                .applyTransaction(any());
    }

    @Test
    void shouldReturn503WhenCircuitBreakerRejectsAccountServiceCall()
            throws Exception {

        doThrow(new AccountServiceUnavailableException(
                "Account Service circuit breaker is open",
                new RuntimeException("Circuit breaker open")
        ))
                .when(accountServiceClient)
                .applyTransaction(any());

        HttpResponse<String> postResponse = sendEvent("""
            {
              "eventId": "evt-circuit-open",
              "accountId": "acct-123",
              "type": "CREDIT",
              "amount": 150.00,
              "currency": "USD",
              "eventTimestamp": "2026-05-15T14:02:11Z"
            }
            """);

        assertThat(postResponse.statusCode()).isEqualTo(503);
        assertThat(postResponse.body())
                .contains("Account Service circuit breaker is open");

        HttpResponse<String> getResponse = get(
                "/events/evt-circuit-open"
        );

        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertThat(getResponse.body())
                .contains("\"eventId\":\"evt-circuit-open\"");
    }

    @Test
    void shouldExposeEventSubmissionMetrics() throws Exception {
        sendEvent("""
            {
              "eventId": "evt-metric-created",
              "accountId": "acct-metrics",
              "type": "CREDIT",
              "amount": 10.00,
              "currency": "USD",
              "eventTimestamp": "2026-05-15T14:02:11Z"
            }
            """);

        sendEvent("""
            {
              "eventId": "evt-metric-created",
              "accountId": "acct-metrics",
              "type": "CREDIT",
              "amount": 10.00,
              "currency": "USD",
              "eventTimestamp": "2026-05-15T14:02:11Z"
            }
            """);

        HttpResponse<String> response = get(
                "/metrics/event.ledger.events.submitted"
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"name\":\"event.ledger.events.submitted\"");
        assertThat(response.body())
                .contains("\"tag\":\"result\"");
        assertThat(response.body())
                .contains("\"values\":[\"created\",\"duplicate\",\"failed\"]");
    }

    private HttpResponse<String> sendEvent(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/events"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
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
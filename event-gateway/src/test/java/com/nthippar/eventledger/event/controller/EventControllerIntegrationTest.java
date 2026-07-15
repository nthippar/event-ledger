package com.nthippar.eventledger.event.controller;

import com.nthippar.eventledger.event.repository.LedgerEventRepository;
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
class EventControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private LedgerEventRepository repository;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
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
}
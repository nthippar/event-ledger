package com.nthippar.eventledger.account.tracing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TraceIdFilterIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void shouldReuseIncomingTraceId() throws Exception {
        String traceId = "trace-account-test-001";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + port + "/health"
                ))
                .header(TraceConstants.TRACE_HEADER, traceId)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);

        assertThat(
                response.headers()
                        .firstValue(TraceConstants.TRACE_HEADER)
        ).contains(traceId);
    }

    @Test
    void shouldGenerateTraceIdWhenRequestDoesNotProvideOne()
            throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + port + "/health"
                ))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        String traceId = response.headers()
                .firstValue(TraceConstants.TRACE_HEADER)
                .orElseThrow();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(traceId).isNotBlank();

        UUID.fromString(traceId);
    }
}
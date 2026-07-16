package com.nthippar.eventledger.event.integration;

import com.nthippar.eventledger.account.AccountServiceApplication;
import com.nthippar.eventledger.event.EventGatewayApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayAccountIntegrationTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void shouldApplyGatewayEventToAccountService() throws Exception {
        try (
                ConfigurableApplicationContext accountContext =
                        startAccountService()
        ) {
            int accountPort = localPort(accountContext);

            try (
                    ConfigurableApplicationContext gatewayContext =
                            startGateway(accountPort)
            ) {
                int gatewayPort = localPort(gatewayContext);

                HttpResponse<String> eventResponse =
                        submitEvent(gatewayPort);

                assertThat(eventResponse.statusCode()).isEqualTo(201);
                assertThat(eventResponse.body())
                        .contains("\"eventId\":\"evt-integration-001\"");

                HttpResponse<String> balanceResponse =
                        getBalance(accountPort);

                assertThat(balanceResponse.statusCode()).isEqualTo(200);
                assertThat(balanceResponse.body())
                        .contains("\"accountId\":\"acct-integration\"");
                assertThat(balanceResponse.body())
                        .contains("\"balance\":150.00");
            }
        }
    }

    private ConfigurableApplicationContext startAccountService() {
        return new SpringApplicationBuilder(
                AccountServiceApplication.class
        ).run(
                "--server.port=0",
                "--spring.datasource.url="
                        + "jdbc:h2:mem:account-e2e;"
                        + "DB_CLOSE_DELAY=-1",
                "--spring.jpa.hibernate.ddl-auto=create-drop"
        );
    }

    private ConfigurableApplicationContext startGateway(
            int accountPort
    ) {
        return new SpringApplicationBuilder(
                EventGatewayApplication.class
        ).run(
                "--server.port=0",
                "--spring.datasource.url="
                        + "jdbc:h2:mem:gateway-e2e;"
                        + "DB_CLOSE_DELAY=-1",
                "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--account-service.base-url="
                        + "http://localhost:"
                        + accountPort
        );
    }

    private int localPort(
            ConfigurableApplicationContext context
    ) {
        return Integer.parseInt(
                context.getEnvironment()
                        .getRequiredProperty("local.server.port")
        );
    }

    private HttpResponse<String> submitEvent(
            int gatewayPort
    ) throws Exception {
        String requestBody = """
                {
                  "eventId": "evt-integration-001",
                  "accountId": "acct-integration",
                  "type": "CREDIT",
                  "amount": 150.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z",
                  "metadata": {
                    "source": "integration-test"
                  }
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:"
                                + gatewayPort
                                + "/events"
                ))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> getBalance(
            int accountPort
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:"
                                + accountPort
                                + "/accounts/acct-integration/balance"
                ))
                .GET()
                .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }
}
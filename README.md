# Event Ledger

## Overview

Event Ledger is a distributed microservice system built with Java 21 and Spring Boot.

The application consists of two independently deployable microservices that communicate synchronously over REST APIs.
- **Event Gateway** – Receives transaction events, validates requests, persists ledger events, and forwards transactions to the Account Service.
- **Account Service** – Maintains account balances and transaction history by applying incoming ledger events.

The services communicate synchronously using REST APIs.

---

## Architecture

```
                +----------------------+
                |      Client          |
                +----------+-----------+
                           |
                           |
                    POST /events
                           |
                           ▼
                +----------------------+
                |    Event Gateway     |
                |----------------------|
                | Event Persistence    |
                | Validation           |
                | Idempotency          |
                | Circuit Breaker      |
                | Distributed Tracing  |
                +----------+-----------+
                           |
                 REST API (HTTP)
                           |
                           ▼
                +----------------------+
                |   Account Service    |
                |----------------------|
                | Transaction History  |
                | Balance Calculation  |
                | Distributed Tracing  |
                +----------------------+
```

---

## Technology Stack

- Java 21
- Spring Boot
- Spring Data JPA
- Spring Web
- H2 Database
- Maven
- Docker Compose
- Micrometer
- Resilience4j Circuit Breaker

---

## Features

### Event Gateway

- Accept ledger events
- Retrieve events
- Event persistence
- Validation
- Idempotent event processing
- Circuit breaker protection
- Distributed tracing
- Structured JSON logging
- Custom metrics

### Account Service

- Apply transactions
- Maintain balances
- Transaction history
- Idempotent processing
- Distributed tracing
- Structured JSON logging

---

## Running Locally

### Build

```bash
mvn clean verify
```

### Start Account Service

```bash
cd account-service
mvn spring-boot:run
```

### Start Event Gateway

```bash
cd event-gateway
mvn spring-boot:run
```

---

## Running with Docker

Build images

```bash
docker compose build
```

Start services

```bash
docker compose up -d
```

Gateway

```
http://localhost:8080
```

Account Service

```
http://localhost:8081
```

---

## Health Endpoints

Gateway

```
GET /health
```

Account Service

```
GET /health
```

---

## Example Event

```http
POST /events
```

```json
{
  "eventId": "evt-001",
  "accountId": "acct-001",
  "type": "CREDIT",
  "amount": 100.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z",
  "metadata": {
    "source": "manual-test"
  }
}
```

---

## Resiliency

The Event Gateway uses a Resilience4j Circuit Breaker when communicating with the Account Service.

If the Account Service becomes unavailable:

- new event processing returns HTTP 503
- events remain persisted
- event retrieval continues to work
- the circuit automatically recovers after the configured wait duration

---

## Distributed Tracing

Every request is assigned an `X-Trace-Id`.

If the client supplies a trace ID, it is reused.

Otherwise, the Gateway generates a new UUID.

The trace ID is:

- propagated to the Account Service
- stored in SLF4J MDC
- returned in response headers
- included in structured ECS JSON logs

---

## Structured Logging

Both services emit ECS JSON logs including:

- timestamp
- log level
- logger
- service name
- trace ID
- message

---

## Custom Metrics

The Gateway exposes the custom metric:

```
event.ledger.events.submitted
```

Result tags:

- created
- duplicate
- failed

Metrics are available through Spring Boot Actuator.

Example:

```
GET /metrics/event.ledger.events.submitted
```

---
### Negative balances

The system permits negative account balances because the requirements
define the balance as the sum of credits minus the sum of debits.
Rejecting debits based on the balance at arrival time would make results
dependent on delivery order and conflict with the out-of-order event
requirement.

## Testing

The project includes:

- Unit tests
- Integration tests
- End-to-end Gateway → Account Service tests

Run all tests:

```bash
mvn clean verify
```

---

## Project Structure

```
event-ledger
│
├── account-service
├── event-gateway
├── docker-compose.yml
├── mvnw
├── mvnw.cmd
├── pom.xml
└── README.md
```

## License

This project was developed as part of a technical assessment and is intended for evaluation purposes.
package com.nthippar.eventledger.event.service;

import com.nthippar.eventledger.event.api.CreateEventRequest;
import com.nthippar.eventledger.event.api.EventResponse;
import com.nthippar.eventledger.event.client.AccountServiceClient;
import com.nthippar.eventledger.event.domain.EventProcessingStatus;
import com.nthippar.eventledger.event.domain.EventType;
import com.nthippar.eventledger.event.domain.LedgerEvent;
import com.nthippar.eventledger.event.error.AccountServiceUnavailableException;
import com.nthippar.eventledger.event.mapper.LedgerEventMapper;
import com.nthippar.eventledger.event.repository.LedgerEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class LedgerEventServiceTest {

    @Mock
    private LedgerEventRepository repository;

    @Mock
    private LedgerEventMapper mapper;

    @Mock
    private AccountServiceClient accountServiceClient;

    private LedgerEventService service;

    @BeforeEach
    void setUp() {
        service = new LedgerEventService(
                repository,
                mapper,
                accountServiceClient
        );
    }

    @Test
    void shouldPersistAndReturnNewEvent() {
        CreateEventRequest request = request("evt-001");

        LedgerEvent entity = entity("evt-001");
        EventResponse response = response("evt-001");

        when(repository.findById("evt-001"))
                .thenReturn(Optional.empty());

        when(mapper.toEntity(request))
                .thenReturn(entity);

        when(repository.saveAndFlush(entity))
                .thenReturn(entity);

        when(accountServiceClient.applyTransaction(entity))
                .thenReturn(null);

        when(mapper.toResponse(entity))
                .thenReturn(response);

        EventSubmissionResult result = service.submit(request);

        assertThat(result.created()).isTrue();
        assertThat(result.event()).isEqualTo(response);
        assertThat(entity.getProcessingStatus()).isEqualTo(EventProcessingStatus.APPLIED);

        verify(repository, times(2)).saveAndFlush(entity);
        verify(accountServiceClient).applyTransaction(entity);
    }

    @Test
    void shouldReturnExistingEventWithoutSavingDuplicate() {
        CreateEventRequest request = request("evt-001");

        LedgerEvent existing = entity("evt-001");
        existing.markApplied();

        EventResponse response = response("evt-001");

        when(repository.findById("evt-001"))
                .thenReturn(Optional.of(existing));

        when(mapper.toResponse(existing))
                .thenReturn(response);

        EventSubmissionResult result = service.submit(request);

        assertThat(result.created()).isFalse();
        assertThat(result.event()).isEqualTo(response);

        verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verify(accountServiceClient, never())
                .applyTransaction(existing);
    }

    @Test
    void shouldMarkNewEventFailedWhenAccountServiceIsUnavailable() {
        CreateEventRequest request = request("evt-failed");

        LedgerEvent entity = entity("evt-failed");

        when(repository.findById("evt-failed"))
                .thenReturn(Optional.empty());

        when(mapper.toEntity(request))
                .thenReturn(entity);

        when(repository.saveAndFlush(entity))
                .thenReturn(entity);

        when(accountServiceClient.applyTransaction(entity))
                .thenThrow(new AccountServiceUnavailableException(
                        "Account Service is currently unavailable",
                        new RuntimeException("Connection refused")
                ));

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOf(AccountServiceUnavailableException.class)
                .hasMessage("Account Service is currently unavailable");

        assertThat(entity.getProcessingStatus())
                .isEqualTo(EventProcessingStatus.FAILED);

        verify(repository, times(2)).saveAndFlush(entity);
        verify(accountServiceClient).applyTransaction(entity);
    }

    @Test
    void shouldRetryExistingFailedEvent() {
        CreateEventRequest request = request("evt-retry");

        LedgerEvent existing = entity("evt-retry");
        existing.markFailed();

        EventResponse response = response("evt-retry");

        when(repository.findById("evt-retry"))
                .thenReturn(Optional.of(existing));

        when(accountServiceClient.applyTransaction(existing))
                .thenReturn(null);

        when(repository.saveAndFlush(existing))
                .thenReturn(existing);

        when(mapper.toResponse(existing))
                .thenReturn(response);

        EventSubmissionResult result = service.submit(request);

        assertThat(result.created()).isFalse();
        assertThat(result.event()).isEqualTo(response);
        assertThat(existing.getProcessingStatus())
                .isEqualTo(EventProcessingStatus.APPLIED);

        verify(accountServiceClient).applyTransaction(existing);
        verify(repository).saveAndFlush(existing);
    }

    private CreateEventRequest request(String eventId) {
        return new CreateEventRequest(
                eventId,
                "acct-123",
                EventType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z"),
                Map.of(
                        "source", "mainframe-batch",
                        "batchId", "B-9042"
                )
        );
    }

    private LedgerEvent entity(String eventId) {
        return new LedgerEvent(
                eventId,
                "acct-123",
                EventType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z"),
                """
                {"source":"mainframe-batch","batchId":"B-9042"}
                """
        );
    }

    private EventResponse response(String eventId) {
        return new EventResponse(
                eventId,
                "acct-123",
                EventType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z"),
                Map.of(
                        "source", "mainframe-batch",
                        "batchId", "B-9042"
                )
        );
    }
}
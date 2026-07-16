package com.nthippar.eventledger.event.service;

import com.nthippar.eventledger.event.api.CreateEventRequest;
import com.nthippar.eventledger.event.api.EventResponse;
import com.nthippar.eventledger.event.client.AccountServiceClient;
import com.nthippar.eventledger.event.domain.EventProcessingStatus;
import com.nthippar.eventledger.event.domain.LedgerEvent;
import com.nthippar.eventledger.event.error.AccountServiceUnavailableException;
import com.nthippar.eventledger.event.error.EventNotFoundException;
import com.nthippar.eventledger.event.mapper.LedgerEventMapper;
import com.nthippar.eventledger.event.repository.LedgerEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LedgerEventService {

    private final LedgerEventRepository repository;
    private final LedgerEventMapper mapper;
    private final AccountServiceClient accountServiceClient;
    private static final Logger log = LoggerFactory.getLogger(LedgerEventService.class);

    public LedgerEventService(
            LedgerEventRepository repository,
            LedgerEventMapper mapper,
            AccountServiceClient accountServiceClient
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.accountServiceClient = accountServiceClient;
    }

    @Transactional(readOnly = true)
    public EventResponse getById(String eventId) {
        return repository.findById(eventId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getByAccount(String accountId) {
        return repository.findByAccountIdOrderByEventTimestampAsc(accountId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(
            noRollbackFor = AccountServiceUnavailableException.class
    )
    public EventSubmissionResult submit(CreateEventRequest request) {
        log.info(
                "Submitting event eventId={} accountId={}",
                request.eventId(),
                request.accountId()
        );

        return repository.findById(request.eventId())
                .map(this::processExistingEvent)
                .orElseGet(() -> persistAndProcessNewEvent(request));
    }

    private EventSubmissionResult persistAndProcessNewEvent(
            CreateEventRequest request
    ) {
        LedgerEvent savedEvent = repository.saveAndFlush(
                mapper.toEntity(request)
        );

        return processEvent(savedEvent, true);
    }

    private EventSubmissionResult processExistingEvent(
            LedgerEvent existing
    ) {
        if (existing.getProcessingStatus() == EventProcessingStatus.APPLIED) {

            return new EventSubmissionResult(
                    mapper.toResponse(existing),
                    false
            );
        }

        return processEvent(existing, false);
    }

    private EventSubmissionResult processEvent(
            LedgerEvent event,
            boolean created
    ) {
        try {
            accountServiceClient.applyTransaction(event);

            event.markApplied();
            repository.saveAndFlush(event);

            return new EventSubmissionResult(
                    mapper.toResponse(event),
                    created
            );
        } catch (AccountServiceUnavailableException exception) {
            event.markFailed();
            repository.saveAndFlush(event);

            throw exception;
        }
    }
}
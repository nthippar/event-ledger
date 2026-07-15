package com.nthippar.eventledger.event.service;

import com.nthippar.eventledger.event.api.CreateEventRequest;
import com.nthippar.eventledger.event.api.EventResponse;
import com.nthippar.eventledger.event.domain.LedgerEvent;
import com.nthippar.eventledger.event.error.EventNotFoundException;
import com.nthippar.eventledger.event.mapper.LedgerEventMapper;
import com.nthippar.eventledger.event.repository.LedgerEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LedgerEventService {

    private final LedgerEventRepository repository;
    private final LedgerEventMapper mapper;

    public LedgerEventService(
            LedgerEventRepository repository,
            LedgerEventMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
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

    @Transactional
    public EventSubmissionResult submit(CreateEventRequest request) {
        return repository.findById(request.eventId())
                .map(existing -> new EventSubmissionResult(
                        mapper.toResponse(existing),
                        false
                ))
                .orElseGet(() -> persistNewEvent(request));
    }

    private EventSubmissionResult persistNewEvent(CreateEventRequest request) {
        try {
            LedgerEvent savedEvent = repository.saveAndFlush(
                    mapper.toEntity(request)
            );

            return new EventSubmissionResult(
                    mapper.toResponse(savedEvent),
                    true
            );
        } catch (DataIntegrityViolationException exception) {
            return repository.findById(request.eventId())
                    .map(existing -> new EventSubmissionResult(
                            mapper.toResponse(existing),
                            false
                    ))
                    .orElseThrow(() -> exception);
        }
    }
}
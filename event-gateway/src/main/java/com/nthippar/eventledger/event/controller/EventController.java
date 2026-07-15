package com.nthippar.eventledger.event.controller;

import com.nthippar.eventledger.event.api.CreateEventRequest;
import com.nthippar.eventledger.event.api.EventResponse;
import com.nthippar.eventledger.event.service.EventSubmissionResult;
import com.nthippar.eventledger.event.service.LedgerEventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController {

    private final LedgerEventService service;

    public EventController(LedgerEventService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EventResponse> submitEvent(
            @Valid @RequestBody CreateEventRequest request
    ) {
        EventSubmissionResult result = service.submit(request);

        if (result.created()) {
            return ResponseEntity
                    .created(URI.create("/events/" + result.event().eventId()))
                    .body(result.event());
        }

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(result.event());
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventResponse> getEvent(
            @PathVariable String eventId
    ) {
        return ResponseEntity.ok(service.getById(eventId));
    }

    @GetMapping
    public ResponseEntity<List<EventResponse>> getEventsByAccount(
            @RequestParam("account") String accountId
    ) {
        return ResponseEntity.ok(service.getByAccount(accountId));
    }
}
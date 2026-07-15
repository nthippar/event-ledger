package com.nthippar.eventledger.event.service;

import com.nthippar.eventledger.event.api.EventResponse;

public record EventSubmissionResult(
        EventResponse event,
        boolean created
) {
}
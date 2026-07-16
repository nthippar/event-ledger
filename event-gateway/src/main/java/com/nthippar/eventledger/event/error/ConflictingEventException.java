package com.nthippar.eventledger.event.error;

public class ConflictingEventException extends RuntimeException {

    public ConflictingEventException(String eventId) {
        super("A different event already exists for eventId: " + eventId);
    }
}
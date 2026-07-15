package com.nthippar.eventledger.event.error;

public class EventNotFoundException extends RuntimeException{
    public EventNotFoundException(String eventId) {
        super("Event with ID " + eventId + " not found.");
    }
}

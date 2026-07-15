package com.nthippar.eventledger.account.error;

public class ConflictingTransactionException extends RuntimeException {

    public ConflictingTransactionException(String eventId) {
        super("A different transaction already exists for eventId: " + eventId);
    }
}
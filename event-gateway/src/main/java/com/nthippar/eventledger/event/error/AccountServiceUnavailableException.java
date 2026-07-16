package com.nthippar.eventledger.event.error;

public class AccountServiceUnavailableException extends RuntimeException {

    public AccountServiceUnavailableException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
package com.payflow.transaction.exception;

/** account-service is down, slow, or the circuit is open. Outcome of the call is unknown. */
public class AccountServiceUnavailableException extends RuntimeException {
    public AccountServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

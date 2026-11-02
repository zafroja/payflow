package com.payflow.transaction.exception;

import org.springframework.http.HttpStatus;

/**
 * account-service answered with a 4xx: the request reached it and was refused on purpose.
 * Configured as an ignored exception in the circuit breaker, because a customer with no money
 * is not a sign that account-service is unhealthy.
 */
public class TransferRejectedException extends BusinessException {

    private final boolean retriable;

    public TransferRejectedException(HttpStatus status, String code, String message) {
        super(status, code, message);
        // 409 = row lock timeout / concurrent update: try again later
        this.retriable = status == HttpStatus.CONFLICT;
    }

    public boolean isRetriable() {
        return retriable;
    }
}

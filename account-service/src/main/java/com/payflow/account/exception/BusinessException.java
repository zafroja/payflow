package com.payflow.account.exception;

import org.springframework.http.HttpStatus;

/**
 * Base for every business rule violation. Carries a stable machine-readable code so API clients
 * and other services (transaction-service) can react without parsing messages.
 */
public abstract class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    protected BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

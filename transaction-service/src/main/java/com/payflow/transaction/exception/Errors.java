package com.payflow.transaction.exception;

import org.springframework.http.HttpStatus;

/** Factory methods for this service's business errors (keeps call sites short and codes consistent). */
public final class Errors {

    public static BusinessException notFound(String reference) {
        return new BusinessException(HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND", "Transfer " + reference + " not found");
    }

    public static BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    public static BusinessException idempotencyKeyReused() {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED",
                "This Idempotency-Key was already used for a different transfer");
    }

    public static BusinessException tooManyTransfers(int limit) {
        return new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "VELOCITY_LIMIT",
                "More than " + limit + " transfers per minute is not allowed");
    }

    private Errors() {
    }
}

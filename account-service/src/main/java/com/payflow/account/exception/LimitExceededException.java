package com.payflow.account.exception;

import org.springframework.http.HttpStatus;

public class LimitExceededException extends BusinessException {
    public LimitExceededException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "LIMIT_EXCEEDED", message);
    }
}

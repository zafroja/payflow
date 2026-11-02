package com.payflow.account.exception;

import com.payflow.account.domain.AccountStatus;
import org.springframework.http.HttpStatus;

public class AccountNotActiveException extends BusinessException {
    public AccountNotActiveException(String accountNumber, AccountStatus status) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_NOT_ACTIVE",
                "Account " + accountNumber + " is " + status);
    }
}

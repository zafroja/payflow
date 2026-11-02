package com.payflow.account.exception;

import org.springframework.http.HttpStatus;

/** Also used when the caller does not own the account, so account numbers cannot be probed. */
public class AccountNotFoundException extends BusinessException {
    public AccountNotFoundException(String accountNumber) {
        super(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account " + accountNumber + " not found");
    }
}

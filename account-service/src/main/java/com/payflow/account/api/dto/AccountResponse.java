package com.payflow.account.api.dto;

import com.payflow.account.domain.Account;
import com.payflow.account.domain.AccountStatus;
import com.payflow.account.domain.AccountType;

import java.math.BigDecimal;
import java.time.Instant;

/** What we return AND what we cache in Redis (an immutable record, never the JPA entity). */
public record AccountResponse(
        String accountNumber,
        Long ownerUserId,
        String ownerName,
        AccountType type,
        AccountStatus status,
        BigDecimal balance,
        String currency,
        Instant openedAt,
        Instant updatedAt) {

    public static AccountResponse from(Account a) {
        return new AccountResponse(
                a.getAccountNumber(),
                a.getCustomer().getUserId(),
                a.getCustomer().getDisplayName(),
                a.getType(),
                a.getStatus(),
                a.getBalance(),
                a.getCurrency(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }
}

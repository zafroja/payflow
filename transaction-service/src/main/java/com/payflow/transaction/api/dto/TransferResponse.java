package com.payflow.transaction.api.dto;

import com.payflow.transaction.domain.Transfer;
import com.payflow.transaction.domain.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(
        String reference,
        String fromAccount,
        String toAccount,
        BigDecimal amount,
        String description,
        TransferStatus status,
        String failureCode,
        String failureReason,
        int attempts,
        boolean replayed,
        Instant createdAt,
        Instant updatedAt) {

    public static TransferResponse from(Transfer t) {
        return from(t, false);
    }

    public static TransferResponse replayOf(Transfer t) {
        return from(t, true);
    }

    private static TransferResponse from(Transfer t, boolean replayed) {
        return new TransferResponse(t.getReference(), t.getFromAccount(), t.getToAccount(), t.getAmount(),
                t.getDescription(), t.getStatus(), t.getFailureCode(), t.getFailureReason(), t.getAttempts(),
                replayed, t.getCreatedAt(), t.getUpdatedAt());
    }
}

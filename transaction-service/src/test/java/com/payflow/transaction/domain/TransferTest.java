package com.payflow.transaction.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TransferTest {

    private final Transfer transfer = new Transfer("key-12345678", 1L, "PF0000000001", "PF0000000002",
            new BigDecimal("10.50"), "rent");

    @Test
    void sameIdempotencyKeyMustMeanSameRequest() {
        assertThat(transfer.matches("PF0000000001", "PF0000000002", new BigDecimal("10.5"))).isTrue();
        assertThat(transfer.matches("PF0000000001", "PF0000000002", new BigDecimal("10.51"))).isFalse();
        assertThat(transfer.matches("PF0000000001", "PF0000000003", new BigDecimal("10.50"))).isFalse();
    }

    @Test
    void lifecycleTransitions() {
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
        transfer.recordAttempt();
        transfer.markAwaitingRetry("down");
        assertThat(transfer.getFailureCode()).isEqualTo("AWAITING_RETRY");
        transfer.recordAttempt();
        transfer.markCompleted();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.getFailureCode()).isNull();
        assertThat(transfer.getAttempts()).isEqualTo(2);
        assertThat(transfer.getReference()).startsWith("TRF-");
    }
}

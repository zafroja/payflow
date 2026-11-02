package com.payflow.account.api.dto;

import java.math.BigDecimal;

public record TransferResult(String reference, Outcome outcome, BigDecimal sourceBalanceAfter) {

    public enum Outcome {
        APPLIED,
        /** Same reference seen before: nothing moved this time (safe retry). */
        ALREADY_APPLIED
    }
}

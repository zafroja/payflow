package com.payflow.account.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Sent by transaction-service. reference makes the call idempotent. */
public record InternalTransferRequest(
        @NotBlank @Size(max = 64) String reference,
        @NotBlank String fromAccount,
        @NotBlank String toAccount,
        @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @NotNull Long initiatedBy) {
}

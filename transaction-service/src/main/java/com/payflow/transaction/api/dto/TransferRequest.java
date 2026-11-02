package com.payflow.transaction.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferRequest(
        @NotNull @Pattern(regexp = "^PF\\d{10}$") String fromAccount,
        @NotNull @Pattern(regexp = "^PF\\d{10}$") String toAccount,
        @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @Size(max = 140) String description) {
}

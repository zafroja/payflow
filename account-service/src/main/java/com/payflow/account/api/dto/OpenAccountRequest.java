package com.payflow.account.api.dto;

import com.payflow.account.domain.AccountType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** currency is optional; defaults to payflow.account.default-currency. */
public record OpenAccountRequest(
        @NotNull AccountType type,
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO-4217 code such as CAD") String currency) {
}

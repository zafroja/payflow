package com.payflow.account.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/** Type-safe business limits bound from application.yml (payflow.account.*). */
@Validated
@ConfigurationProperties(prefix = "payflow.account")
public record AccountProperties(
        @Min(1) int maxAccountsPerCustomer,
        @NotNull BigDecimal maxSingleDeposit,
        @NotNull String defaultCurrency) {
}

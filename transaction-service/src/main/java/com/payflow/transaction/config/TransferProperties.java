package com.payflow.transaction.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "payflow.transfer")
public record TransferProperties(
        @NotNull BigDecimal maxAmount,
        @Min(1) int maxPerMinute,
        @Min(1) int maxBulkItems,
        @NotNull Duration bulkItemTimeout,
        @NotNull Duration retryPendingAfter,
        @Min(1) int maxAttempts,
        @NotBlank String reportingZone) {
}

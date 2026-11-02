package com.payflow.account.api.dto;

import java.math.BigDecimal;

public record BalanceSummary(Long userId, String currency, BigDecimal totalActiveBalance, int accountCount) {
}

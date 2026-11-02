package com.payflow.transaction.client;

import java.math.BigDecimal;

public record AccountTransferResult(String reference, String outcome, BigDecimal sourceBalanceAfter) {
}

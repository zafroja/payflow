package com.payflow.transaction.client;

import java.math.BigDecimal;

public record AccountTransferRequest(String reference, String fromAccount, String toAccount,
                                     BigDecimal amount, Long initiatedBy) {
}

package com.payflow.transaction.api.dto;

import com.payflow.transaction.domain.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record TransferSummary(
        int days,
        String zone,
        long totalTransfers,
        Map<TransferStatus, Long> countByStatus,
        BigDecimal totalSent,
        BigDecimal averageSent,
        Map<LocalDate, BigDecimal> dailySent,
        List<RecipientTotal> topRecipients,
        TransferResponse largestTransfer) {

    public record RecipientTotal(String toAccount, BigDecimal total, long count) {
    }
}

package com.payflow.transaction.api.dto;

import java.util.List;
import java.util.Map;

public record BulkTransferResponse(int submitted, Map<String, Long> countByOutcome, long elapsedMs,
                                   List<Item> results) {

    /** outcome = a TransferStatus name, or ERROR / TIMEOUT if the item never produced a transfer status. */
    public record Item(String idempotencyKey, String outcome, String reference, String code, String message) {
    }
}

package com.payflow.transaction.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** e.g. a small-business payroll file: each line carries its own idempotency key. */
public record BulkTransferRequest(@NotEmpty List<@Valid @NotNull Item> items) {

    public record Item(@NotBlank @Size(min = 8, max = 64) String idempotencyKey,
                       @NotNull @Valid TransferRequest transfer) {
    }
}

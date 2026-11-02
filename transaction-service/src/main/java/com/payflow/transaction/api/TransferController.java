package com.payflow.transaction.api;

import com.payflow.transaction.api.dto.BulkTransferRequest;
import com.payflow.transaction.api.dto.BulkTransferResponse;
import com.payflow.transaction.api.dto.PageResponse;
import com.payflow.transaction.api.dto.TransferRequest;
import com.payflow.transaction.api.dto.TransferResponse;
import com.payflow.transaction.api.dto.TransferSummary;
import com.payflow.transaction.security.AuthenticatedUser;
import com.payflow.transaction.service.BulkTransferService;
import com.payflow.transaction.service.StatementService;
import com.payflow.transaction.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransferController {

    private final TransferService transferService;
    private final BulkTransferService bulkTransferService;
    private final StatementService statementService;

    public TransferController(TransferService transferService, BulkTransferService bulkTransferService,
                              StatementService statementService) {
        this.transferService = transferService;
        this.bulkTransferService = bulkTransferService;
        this.statementService = statementService;
    }

    /**
     * 201 completed, 202 accepted but pending, 422 rejected (insufficient funds...), 200 on a replay.
     * Clients must send a unique Idempotency-Key per transfer and reuse it when retrying.
     */
    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> transfer(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                     @Valid @RequestBody TransferRequest request) {
        TransferResponse response = transferService.initiate(user, idempotencyKey, request);
        return ResponseEntity.status(httpStatusFor(response)).body(response);
    }

    @PostMapping("/transfers/bulk")
    public BulkTransferResponse bulk(@AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody BulkTransferRequest request) {
        return bulkTransferService.submit(user, request);
    }

    @GetMapping("/transfers/{reference}")
    public TransferResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String reference) {
        return transferService.get(user, reference);
    }

    @GetMapping("/transfers")
    public PageResponse<TransferResponse> history(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return transferService.history(user, pageable);
    }

    @GetMapping("/summary")
    public TransferSummary summary(@AuthenticationPrincipal AuthenticatedUser user,
                                   @RequestParam(defaultValue = "30") int days) {
        return statementService.summary(user, days);
    }

    private static HttpStatus httpStatusFor(TransferResponse response) {
        if (response.replayed()) {
            return HttpStatus.OK;
        }
        return switch (response.status()) {
            case COMPLETED -> HttpStatus.CREATED;
            case FAILED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case PENDING, NEEDS_REVIEW -> HttpStatus.ACCEPTED;
        };
    }
}

package com.payflow.transaction.service;

import com.payflow.transaction.api.dto.PageResponse;
import com.payflow.transaction.api.dto.TransferRequest;
import com.payflow.transaction.api.dto.TransferResponse;
import com.payflow.transaction.client.AccountGateway;
import com.payflow.transaction.client.AccountTransferRequest;
import com.payflow.transaction.concurrency.VelocityGuard;
import com.payflow.transaction.config.TransferProperties;
import com.payflow.transaction.domain.Transfer;
import com.payflow.transaction.exception.AccountServiceUnavailableException;
import com.payflow.transaction.exception.Errors;
import com.payflow.transaction.exception.TransferRejectedException;
import com.payflow.transaction.repository.TransferRepository;
import com.payflow.transaction.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrates a transfer:
 * <pre>
 *   1. Idempotency check     same key + same body -> return the earlier result
 *   2. Velocity check        Redis counter shared by all pods
 *   3. Persist PENDING       short DB transaction; unique key blocks racing duplicates
 *   4. Call account-service  OUTSIDE any DB transaction (never hold a connection during a remote call)
 *   5. Persist the outcome   COMPLETED / FAILED / still PENDING for the reconciliation job
 * </pre>
 * Transactions are programmatic (TransactionTemplate) because the method mixes short DB work
 * with a slow remote call; one @Transactional around everything would hold a DB connection
 * and row locks for the whole HTTP round-trip.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final TransferRepository transfers;
    private final AccountGateway accountGateway;
    private final VelocityGuard velocityGuard;
    private final TransferProperties properties;
    private final TransactionTemplate tx;

    public TransferService(TransferRepository transfers, AccountGateway accountGateway, VelocityGuard velocityGuard,
                           TransferProperties properties, TransactionTemplate tx) {
        this.transfers = transfers;
        this.accountGateway = accountGateway;
        this.velocityGuard = velocityGuard;
        this.properties = properties;
        this.tx = tx;
    }

    public TransferResponse initiate(AuthenticatedUser user, String idempotencyKey, TransferRequest request) {
        return initiate(user, idempotencyKey, request, true);
    }

    /** Used by bulk submission, which applies the velocity check once for the whole batch. */
    public TransferResponse initiateWithinBulk(AuthenticatedUser user, String idempotencyKey, TransferRequest request) {
        return initiate(user, idempotencyKey, request, false);
    }

    private TransferResponse initiate(AuthenticatedUser user, String idempotencyKey, TransferRequest request,
                                      boolean checkVelocity) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw Errors.invalid("Idempotency-Key header must be 1-64 characters");
        }

        // 1. Replay?
        var previous = transfers.findByInitiatedByAndIdempotencyKey(user.userId(), idempotencyKey);
        if (previous.isPresent()) {
            return replay(previous.get(), request);
        }

        if (request.fromAccount().equals(request.toAccount())) {
            throw Errors.invalid("Source and destination accounts must differ");
        }
        if (request.amount().compareTo(properties.maxAmount()) > 0) {
            throw Errors.invalid("Maximum amount per transfer is " + properties.maxAmount());
        }

        // 2. Velocity
        if (checkVelocity) {
            velocityGuard.check(user.userId());
        }

        // 3. Persist PENDING
        Transfer transfer;
        try {
            transfer = tx.execute(status -> transfers.saveAndFlush(new Transfer(idempotencyKey, user.userId(),
                    request.fromAccount(), request.toAccount(), request.amount(), request.description())));
        } catch (DataIntegrityViolationException race) {
            // Another request with the same key won the race; return its result
            Transfer winner = transfers.findByInitiatedByAndIdempotencyKey(user.userId(), idempotencyKey)
                    .orElseThrow(() -> race);
            return replay(winner, request);
        }

        // 4 + 5
        return execute(transfer);
    }

    /** Calls account-service and records the outcome. Also used by the reconciliation job for retries. */
    public TransferResponse execute(Transfer transfer) {
        transfer.recordAttempt();
        try {
            accountGateway.applyTransfer(new AccountTransferRequest(transfer.getReference(),
                    transfer.getFromAccount(), transfer.getToAccount(), transfer.getAmount(),
                    transfer.getInitiatedBy()));
            transfer.markCompleted();
        } catch (TransferRejectedException rejected) {
            if (rejected.isRetriable()) {
                transfer.markAwaitingRetry(rejected.getMessage());
            } else {
                transfer.markFailed(rejected.getCode(), rejected.getMessage());
            }
        } catch (AccountServiceUnavailableException unavailable) {
            // Outcome unknown. Safe to retry later because account-service is idempotent on the reference.
            transfer.markAwaitingRetry("Account service unavailable, will retry automatically");
        }
        Transfer saved = tx.execute(status -> transfers.save(transfer));
        log.info("transfer={} status={} attempts={}", saved.getReference(), saved.getStatus(), saved.getAttempts());
        return TransferResponse.from(saved);
    }

    public TransferResponse get(AuthenticatedUser user, String reference) {
        return transfers.findByReferenceAndInitiatedBy(reference, user.userId())
                .map(TransferResponse::from)
                .orElseThrow(() -> Errors.notFound(reference));
    }

    public PageResponse<TransferResponse> history(AuthenticatedUser user, Pageable pageable) {
        return PageResponse.of(transfers.findByInitiatedBy(user.userId(), pageable).map(TransferResponse::from));
    }

    private static TransferResponse replay(Transfer existing, TransferRequest request) {
        if (!existing.matches(request.fromAccount(), request.toAccount(), request.amount())) {
            throw Errors.idempotencyKeyReused();
        }
        return TransferResponse.replayOf(existing);
    }
}

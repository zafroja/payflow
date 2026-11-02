package com.payflow.transaction.service;

import com.payflow.transaction.api.dto.BulkTransferRequest;
import com.payflow.transaction.api.dto.BulkTransferResponse;
import com.payflow.transaction.api.dto.TransferResponse;
import com.payflow.transaction.concurrency.ExecutorConfig;
import com.payflow.transaction.concurrency.VelocityGuard;
import com.payflow.transaction.config.TransferProperties;
import com.payflow.transaction.exception.BusinessException;
import com.payflow.transaction.exception.Errors;
import com.payflow.transaction.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Processes a batch of transfers in parallel (fan-out / fan-in with CompletableFuture).
 *
 * Why parallel is safe: each item is idempotent and account-service serialises updates to the same
 * account with row locks, so parallelism speeds up the I/O (HTTP + DB round-trips) while the short
 * critical section on each balance stays strictly ordered.
 */
@Service
public class BulkTransferService {

    private final TransferService transferService;
    private final VelocityGuard velocityGuard;
    private final TransferProperties properties;
    private final Executor executor;

    public BulkTransferService(TransferService transferService, VelocityGuard velocityGuard,
                               TransferProperties properties,
                               @Qualifier(ExecutorConfig.TRANSFER_EXECUTOR) Executor executor) {
        this.transferService = transferService;
        this.velocityGuard = velocityGuard;
        this.properties = properties;
        this.executor = executor;
    }

    public BulkTransferResponse submit(AuthenticatedUser user, BulkTransferRequest request) {
        List<BulkTransferRequest.Item> items = request.items();
        if (items.size() > properties.maxBulkItems()) {
            throw Errors.invalid("A batch can contain at most " + properties.maxBulkItems() + " transfers");
        }
        Set<String> seen = new HashSet<>();
        items.stream()
                .map(BulkTransferRequest.Item::idempotencyKey)
                .filter(key -> !seen.add(key))
                .findFirst()
                .ifPresent(dup -> {
                    throw Errors.invalid("Duplicate idempotencyKey in batch: " + dup);
                });

        velocityGuard.check(user.userId()); // one batch counts as one request
        long start = System.nanoTime();

        // Fan out: one task per item on the bounded transfer pool. The user is passed explicitly
        // because the SecurityContext (a ThreadLocal) is not visible on pool threads.
        List<CompletableFuture<BulkTransferResponse.Item>> futures = items.stream()
                .map(item -> CompletableFuture
                        .supplyAsync(() -> transferService.initiateWithinBulk(
                                user, item.idempotencyKey(), item.transfer()), executor)
                        .orTimeout(properties.bulkItemTimeout().toMillis(), TimeUnit.MILLISECONDS)
                        .handle((response, error) -> error == null
                                ? success(item.idempotencyKey(), response)
                                : failure(item.idempotencyKey(), error)))
                .collect(Collectors.toList());

        // Fan in: wait for all; handle() above guarantees no future completes exceptionally
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        List<BulkTransferResponse.Item> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        Map<String, Long> countByOutcome = results.stream()
                .collect(Collectors.groupingBy(BulkTransferResponse.Item::outcome, TreeMap::new, Collectors.counting()));

        return new BulkTransferResponse(results.size(), countByOutcome,
                (System.nanoTime() - start) / 1_000_000, results);
    }

    private static BulkTransferResponse.Item success(String key, TransferResponse r) {
        return new BulkTransferResponse.Item(key, r.status().name(), r.reference(), r.failureCode(), r.failureReason());
    }

    private static BulkTransferResponse.Item failure(String key, Throwable error) {
        Throwable cause = unwrap.apply(error);
        if (cause instanceof TimeoutException) {
            // The task may still finish in the background; the key makes a status check (or re-submit) safe.
            return new BulkTransferResponse.Item(key, "TIMEOUT", null, "TIMEOUT",
                    "Still processing; re-submit with the same idempotencyKey to get the final status");
        }
        String code = cause instanceof BusinessException be ? be.getCode() : "ERROR";
        return new BulkTransferResponse.Item(key, "ERROR", null, code, cause.getMessage());
    }

    private static final Function<Throwable, Throwable> unwrap =
            t -> t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
}

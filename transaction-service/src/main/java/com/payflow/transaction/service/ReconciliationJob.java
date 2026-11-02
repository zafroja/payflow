package com.payflow.transaction.service;

import com.payflow.transaction.config.TransferProperties;
import com.payflow.transaction.domain.Transfer;
import com.payflow.transaction.domain.TransferStatus;
import com.payflow.transaction.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Retries transfers left PENDING because account-service was down or busy.
 *
 * Safe to run on every pod at once: account-service ignores a reference it has already applied,
 * and @Version on Transfer makes a second writer fail instead of overwriting. In production you
 * would still add ShedLock (or a Kubernetes CronJob) so only one pod does the work.
 */
@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final TransferRepository transfers;
    private final TransferService transferService;
    private final TransferProperties properties;
    private final TransactionTemplate tx;

    public ReconciliationJob(TransferRepository transfers, TransferService transferService,
                             TransferProperties properties, TransactionTemplate tx) {
        this.transfers = transfers;
        this.transferService = transferService;
        this.properties = properties;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${payflow.transfer.reconcile-interval:PT30S}", initialDelayString = "PT20S")
    public void retryPendingTransfers() {
        Instant cutoff = Instant.now().minus(properties.retryPendingAfter());
        List<Transfer> stuck = transfers.findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                TransferStatus.PENDING, cutoff);
        if (stuck.isEmpty()) {
            return;
        }
        log.info("Reconciling {} pending transfers", stuck.size());

        for (Transfer transfer : stuck) {
            try {
                if (transfer.getAttempts() >= properties.maxAttempts()) {
                    transfer.markNeedsReview("No confirmation from account-service after "
                            + transfer.getAttempts() + " attempts");
                    tx.executeWithoutResult(status -> transfers.save(transfer));
                    log.error("transfer={} moved to NEEDS_REVIEW", transfer.getReference());
                } else {
                    transferService.execute(transfer);
                }
            } catch (OptimisticLockingFailureException concurrentUpdate) {
                log.debug("transfer={} updated by another worker, skipping", transfer.getReference());
            }
        }
    }
}

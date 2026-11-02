package com.payflow.transaction.repository;

import com.payflow.transaction.domain.Transfer;
import com.payflow.transaction.domain.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findByInitiatedByAndIdempotencyKey(Long initiatedBy, String idempotencyKey);

    Optional<Transfer> findByReferenceAndInitiatedBy(String reference, Long initiatedBy);

    Page<Transfer> findByInitiatedBy(Long initiatedBy, Pageable pageable);

    List<Transfer> findByInitiatedByAndCreatedAtAfter(Long initiatedBy, Instant since);

    /** Oldest stuck transfers first, in small batches so one run never holds too much in memory. */
    List<Transfer> findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(TransferStatus status, Instant before);
}

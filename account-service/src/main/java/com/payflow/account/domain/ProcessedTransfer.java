package com.payflow.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Idempotency ledger. A transfer reference is recorded in the SAME database transaction as the
 * balance changes, so a retried request (network timeout, circuit-breaker retry, reconciliation job)
 * can never move money twice.
 *
 * Implements Persistable because the id is assigned by us: without it Spring Data would call
 * merge() and run an extra SELECT before every INSERT.
 */
@Entity
@Table(name = "processed_transfers")
public class ProcessedTransfer implements Persistable<String> {

    @Id
    @Column(length = 64)
    private String reference;

    @Column(name = "from_account", nullable = false, length = 20)
    private String fromAccount;

    @Column(name = "to_account", nullable = false, length = 20)
    private String toAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Transient
    private boolean isNew = true;

    protected ProcessedTransfer() {
        // for JPA
    }

    public ProcessedTransfer(String reference, String fromAccount, String toAccount, BigDecimal amount) {
        this.reference = reference;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.processedAt = Instant.now();
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public String getId() {
        return reference;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }
}

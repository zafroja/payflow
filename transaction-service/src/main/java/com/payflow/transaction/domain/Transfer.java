package com.payflow.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One money-transfer request and its lifecycle.
 * The unique (initiated_by, idempotency_key) constraint is the final guard against double
 * submission: even if two identical requests race, the database accepts only one row.
 */
@Entity
@Table(name = "transfers",
        uniqueConstraints = @UniqueConstraint(name = "uk_transfer_idempotency",
                columnNames = {"initiated_by", "idempotency_key"}),
        indexes = {
                @Index(name = "idx_transfers_initiator_created", columnList = "initiated_by, created_at"),
                @Index(name = "idx_transfers_status_created", columnList = "status, created_at")
        })
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Public id, also sent to account-service as its idempotency reference. */
    @Column(nullable = false, unique = true, length = 64)
    private String reference;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "initiated_by", nullable = false)
    private Long initiatedBy;

    @Column(name = "from_account", nullable = false, length = 20)
    private String fromAccount;

    @Column(name = "to_account", nullable = false, length = 20)
    private String toAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 140)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status = TransferStatus.PENDING;

    @Column(name = "failure_code", length = 40)
    private String failureCode;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(nullable = false)
    private int attempts;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Transfer() {
        // for JPA
    }

    public Transfer(String idempotencyKey, Long initiatedBy, String fromAccount, String toAccount,
                    BigDecimal amount, String description) {
        this.reference = "TRF-" + UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.initiatedBy = initiatedBy;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.description = description;
    }

    public void recordAttempt() {
        attempts++;
    }

    public void markCompleted() {
        status = TransferStatus.COMPLETED;
        failureCode = null;
        failureReason = null;
    }

    public void markFailed(String code, String reason) {
        status = TransferStatus.FAILED;
        failureCode = code;
        failureReason = truncate(reason);
    }

    public void markAwaitingRetry(String reason) {
        status = TransferStatus.PENDING;
        failureCode = "AWAITING_RETRY";
        failureReason = truncate(reason);
    }

    public void markNeedsReview(String reason) {
        status = TransferStatus.NEEDS_REVIEW;
        failureCode = "RETRIES_EXHAUSTED";
        failureReason = truncate(reason);
    }

    /** Same idempotency key must mean the same request; anything else is a client bug. */
    public boolean matches(String from, String to, BigDecimal amt) {
        return fromAccount.equals(from) && toAccount.equals(to) && amount.compareTo(amt) == 0;
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 255 ? s : s.substring(0, 255);
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Long getInitiatedBy() { return initiatedBy; }
    public String getFromAccount() { return fromAccount; }
    public String getToAccount() { return toAccount; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public TransferStatus getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
    public String getFailureReason() { return failureReason; }
    public int getAttempts() { return attempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

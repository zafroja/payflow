package com.payflow.transaction.domain;

public enum TransferStatus {
    /** Recorded, money not confirmed moved yet (in flight, or waiting for a retry). */
    PENDING,
    COMPLETED,
    /** Rejected by a business rule (insufficient funds, frozen account...). No money moved. */
    FAILED,
    /**
     * Retries exhausted while account-service was unreachable. The money MAY have moved
     * (e.g. a timeout after the debit committed), so a human or a later job must check;
     * we never report this as FAILED.
     */
    NEEDS_REVIEW
}

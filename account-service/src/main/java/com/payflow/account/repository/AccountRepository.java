package com.payflow.account.repository;

import com.payflow.account.domain.Account;
import com.payflow.account.domain.AccountStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    // Derived queries: Spring Data builds the JPQL from the method name
    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    Page<Account> findByStatus(AccountStatus status, Pageable pageable);

    /**
     * SELECT ... FOR UPDATE. Holds a row lock until the transaction ends, so two transfers
     * touching the same account run one after another. The hint caps the wait at 3s on databases
     * that honour it; for Postgres the same limit is set via lock_timeout in application.yml.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select a from Account a where a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);

    /** JOIN FETCH loads accounts and their customer in one query (no N+1). */
    @Query("select a from Account a join fetch a.customer c where c.userId = :userId order by a.createdAt")
    List<Account> findAllByOwner(@Param("userId") Long userId);

    @Query("""
            select coalesce(sum(a.balance), 0)
            from Account a
            where a.customer.userId = :userId and a.status = :status and a.currency = :currency
            """)
    BigDecimal totalBalance(@Param("userId") Long userId,
                            @Param("status") AccountStatus status,
                            @Param("currency") String currency);

    /**
     * Bulk JPQL update: one statement instead of load-modify-save per row.
     * "versioned" makes Hibernate bump @Version so in-flight optimistic updates still fail safely;
     * clearAutomatically drops now-stale entities from the persistence context.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update versioned Account a set a.status = :status where a.customer.id = :customerId")
    int updateStatusForCustomer(@Param("customerId") Long customerId, @Param("status") AccountStatus status);
}

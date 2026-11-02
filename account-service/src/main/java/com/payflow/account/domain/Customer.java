package com.payflow.account.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Banking customer. userId is the id issued by auth-service: services share ids, never tables.
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    /**
     * LAZY by default for @OneToMany. @BatchSize turns N+1 lazy loads into ceil(N/20) queries
     * when many customers' accounts are touched; CustomerRepository also offers an @EntityGraph.
     */
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 20)
    private List<Account> accounts = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected Customer() {
        // for JPA
    }

    public Customer(Long userId, String displayName) {
        this.userId = userId;
        this.displayName = displayName;
    }

    /** Keeps both sides of the bidirectional relationship in sync. */
    public void addAccount(Account account) {
        accounts.add(account);
        account.assignTo(this);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public List<Account> getAccounts() { return Collections.unmodifiableList(accounts); }
    public Instant getCreatedAt() { return createdAt; }
}

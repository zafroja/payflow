package com.payflow.account.repository;

import com.payflow.account.domain.Customer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByUserId(Long userId);

    /** Fetches the customer and the accounts collection in a single query. */
    @EntityGraph(attributePaths = "accounts")
    Optional<Customer> findWithAccountsByUserId(Long userId);
}

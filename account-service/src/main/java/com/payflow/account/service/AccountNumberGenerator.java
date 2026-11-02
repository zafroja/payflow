package com.payflow.account.service;

import com.payflow.account.repository.AccountRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.stream.IntStream;

/** Generates unguessable account numbers like PF4821093375. */
@Component
public class AccountNumberGenerator {

    private static final int MAX_ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom(); // thread-safe
    private final AccountRepository accounts;

    public AccountNumberGenerator(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public String next() {
        return IntStream.range(0, MAX_ATTEMPTS)
                .mapToObj(i -> "PF" + String.format("%010d", random.nextLong(10_000_000_000L)))
                .filter(candidate -> !accounts.existsByAccountNumber(candidate))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not generate a unique account number"));
    }
}

package com.payflow.account.service;

import com.payflow.account.api.dto.AccountResponse;
import com.payflow.account.api.dto.BalanceSummary;
import com.payflow.account.api.dto.InternalTransferRequest;
import com.payflow.account.api.dto.OpenAccountRequest;
import com.payflow.account.api.dto.PageResponse;
import com.payflow.account.api.dto.TransferResult;
import com.payflow.account.audit.Auditable;
import com.payflow.account.config.AccountProperties;
import com.payflow.account.domain.Account;
import com.payflow.account.domain.AccountStatus;
import com.payflow.account.domain.Customer;
import com.payflow.account.domain.ProcessedTransfer;
import com.payflow.account.event.AccountOpenedEvent;
import com.payflow.account.event.AccountsChangedEvent;
import com.payflow.account.exception.AccountNotFoundException;
import com.payflow.account.exception.InvalidTransferException;
import com.payflow.account.exception.LimitExceededException;
import com.payflow.account.repository.AccountRepository;
import com.payflow.account.repository.CustomerRepository;
import com.payflow.account.repository.ProcessedTransferRepository;
import com.payflow.account.security.AuthenticatedUser;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AccountService {

    public static final String ACCOUNTS_CACHE = "accounts";

    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final ProcessedTransferRepository processedTransfers;
    private final AccountNumberGenerator numberGenerator;
    private final ApplicationEventPublisher events;
    private final AccountProperties properties;

    public AccountService(AccountRepository accounts, CustomerRepository customers,
                          ProcessedTransferRepository processedTransfers, AccountNumberGenerator numberGenerator,
                          ApplicationEventPublisher events, AccountProperties properties) {
        this.accounts = accounts;
        this.customers = customers;
        this.processedTransfers = processedTransfers;
        this.numberGenerator = numberGenerator;
        this.events = events;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ customer-facing

    @Auditable(action = "OPEN_ACCOUNT")
    @Transactional
    public AccountResponse openAccount(AuthenticatedUser user, OpenAccountRequest request) {
        Customer customer = customers.findWithAccountsByUserId(user.userId())
                .orElseGet(() -> customers.save(new Customer(user.userId(), user.username())));

        if (customer.getAccounts().size() >= properties.maxAccountsPerCustomer()) {
            throw new LimitExceededException("A customer can hold at most "
                    + properties.maxAccountsPerCustomer() + " accounts");
        }

        String currency = request.currency() != null ? request.currency() : properties.defaultCurrency();
        Account account = new Account(numberGenerator.next(), request.type(), currency);
        customer.addAccount(account);
        accounts.save(account);

        events.publishEvent(new AccountOpenedEvent(account.getAccountNumber(), user.userId(), request.type().name()));
        return AccountResponse.from(account);
    }

    /**
     * Cache-aside read: first call hits Postgres and stores the DTO in Redis; later calls are served
     * from Redis until a change evicts the entry (see AccountEventListeners) or the TTL expires.
     * The ownership check is done by the caller on the cached DTO, so it is never skipped on a cache hit.
     */
    @Cacheable(cacheNames = ACCOUNTS_CACHE, key = "#accountNumber")
    @Transactional(readOnly = true)
    public AccountResponse getAccount(String accountNumber) {
        return accounts.findByAccountNumber(accountNumber)
                .map(AccountResponse::from)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> myAccounts(AuthenticatedUser user) {
        return accounts.findAllByOwner(user.userId()).stream()
                .map(AccountResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BalanceSummary balanceSummary(AuthenticatedUser user, String currency) {
        String ccy = currency != null ? currency : properties.defaultCurrency();
        BigDecimal total = accounts.totalBalance(user.userId(), AccountStatus.ACTIVE, ccy);
        int count = accounts.findAllByOwner(user.userId()).size();
        return new BalanceSummary(user.userId(), ccy, total, count);
    }

    @Auditable(action = "DEPOSIT")
    @Transactional
    public AccountResponse deposit(AuthenticatedUser user, String accountNumber, BigDecimal amount) {
        if (amount.compareTo(properties.maxSingleDeposit()) > 0) {
            throw new LimitExceededException("Single deposit limit is " + properties.maxSingleDeposit());
        }
        Account account = lockOwned(accountNumber, user);
        account.credit(amount);
        events.publishEvent(new AccountsChangedEvent(Set.of(accountNumber)));
        return AccountResponse.from(account);
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> listByStatus(AccountStatus status, Pageable pageable) {
        return PageResponse.of(accounts.findByStatus(status, pageable).map(AccountResponse::from));
    }

    @Auditable(action = "FREEZE_ACCOUNT")
    @Transactional
    public AccountResponse freeze(String accountNumber) {
        Account account = accounts.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
        account.freeze();
        events.publishEvent(new AccountsChangedEvent(Set.of(accountNumber)));
        return AccountResponse.from(account);
    }

    /** Fraud response: freeze every account of a customer in one SQL UPDATE. */
    @Auditable(action = "FREEZE_CUSTOMER")
    @Transactional
    public int freezeCustomer(Long userId) {
        Customer customer = customers.findWithAccountsByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException("of user " + userId));
        Set<String> numbers = customer.getAccounts().stream()
                .map(Account::getAccountNumber)
                .collect(Collectors.toSet());
        int updated = accounts.updateStatusForCustomer(customer.getId(), AccountStatus.FROZEN);
        events.publishEvent(new AccountsChangedEvent(numbers));
        return updated;
    }

    // ------------------------------------------------------------------ service-to-service

    /**
     * Moves money between two accounts atomically and idempotently.
     *
     * <ol>
     *   <li>Lock both rows in a fixed order (lowest account number first). Two opposite transfers
     *       A->B and B->A therefore queue up instead of deadlocking.</li>
     *   <li>Check the idempotency ledger AFTER taking the locks, so a concurrent duplicate waits for
     *       the first one to commit and then sees its reference.</li>
     *   <li>Debit, credit and record the reference in one transaction: all or nothing.</li>
     * </ol>
     */
    @Auditable(action = "APPLY_TRANSFER")
    @Transactional
    public TransferResult applyTransfer(InternalTransferRequest request) {
        String from = request.fromAccount();
        String to = request.toAccount();
        if (from.equals(to)) {
            throw new InvalidTransferException("Source and destination must differ");
        }

        boolean fromFirst = from.compareTo(to) < 0;
        Account first = lock(fromFirst ? from : to);
        Account second = lock(fromFirst ? to : from);
        Account source = fromFirst ? first : second;
        Account target = fromFirst ? second : first;

        if (processedTransfers.existsById(request.reference())) {
            return new TransferResult(request.reference(), TransferResult.Outcome.ALREADY_APPLIED, source.getBalance());
        }
        if (!source.isOwnedBy(request.initiatedBy())) {
            throw new AccountNotFoundException(from); // do not reveal that the account exists
        }
        if (!source.getCurrency().equals(target.getCurrency())) {
            throw new InvalidTransferException("Cross-currency transfers are not supported");
        }

        source.debit(request.amount());
        target.credit(request.amount());
        processedTransfers.save(new ProcessedTransfer(request.reference(), from, to, request.amount()));

        events.publishEvent(new AccountsChangedEvent(Set.of(from, to)));
        return new TransferResult(request.reference(), TransferResult.Outcome.APPLIED, source.getBalance());
    }

    // ------------------------------------------------------------------ helpers

    private Account lock(String accountNumber) {
        return accounts.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    private Account lockOwned(String accountNumber, AuthenticatedUser user) {
        Account account = lock(accountNumber);
        if (!account.isOwnedBy(user.userId())) {
            throw new AccountNotFoundException(accountNumber);
        }
        return account;
    }
}

package com.payflow.transaction.client;

import com.payflow.transaction.exception.AccountServiceUnavailableException;
import com.payflow.transaction.exception.TransferRejectedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Wraps the Feign client with a Resilience4j circuit breaker.
 * When account-service keeps failing, the circuit opens and calls fail fast for a while
 * (no threads stuck waiting on timeouts), then a few trial calls decide whether to close it.
 */
@Component
public class AccountGateway {

    private static final Logger log = LoggerFactory.getLogger(AccountGateway.class);

    private final AccountClient accountClient;

    public AccountGateway(AccountClient accountClient) {
        this.accountClient = accountClient;
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "applyTransferFallback")
    public AccountTransferResult applyTransfer(AccountTransferRequest request) {
        return accountClient.applyTransfer(request);
    }

    /**
     * Resilience4j calls this for ANY exception. Business rejections are re-thrown untouched;
     * technical failures (timeouts, 5xx, open circuit) become one well-defined exception type.
     */
    public AccountTransferResult applyTransferFallback(AccountTransferRequest request, Throwable error) {
        if (error instanceof TransferRejectedException rejected) {
            throw rejected;
        }
        log.warn("account-service unavailable for {}: {}", request.reference(), error.toString());
        throw new AccountServiceUnavailableException("account-service unavailable", error);
    }
}

package com.payflow.transaction.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Declarative HTTP client. "account-service" is resolved through Eureka and load-balanced
 * across all healthy instances by Spring Cloud LoadBalancer.
 */
@FeignClient(name = "account-service", path = "/internal/accounts", configuration = AccountClientConfig.class)
public interface AccountClient {

    @PostMapping("/transfers")
    AccountTransferResult applyTransfer(@RequestBody AccountTransferRequest request);
}

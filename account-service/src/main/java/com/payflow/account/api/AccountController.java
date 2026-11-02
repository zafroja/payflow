package com.payflow.account.api;

import com.payflow.account.api.dto.AccountResponse;
import com.payflow.account.api.dto.BalanceSummary;
import com.payflow.account.api.dto.MoneyRequest;
import com.payflow.account.api.dto.OpenAccountRequest;
import com.payflow.account.api.dto.PageResponse;
import com.payflow.account.domain.AccountStatus;
import com.payflow.account.exception.AccountNotFoundException;
import com.payflow.account.security.AuthenticatedUser;
import com.payflow.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse open(@AuthenticationPrincipal AuthenticatedUser user,
                                @Valid @RequestBody OpenAccountRequest request) {
        return accountService.openAccount(user, request);
    }

    @GetMapping("/mine")
    public List<AccountResponse> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return accountService.myAccounts(user);
    }

    @GetMapping("/mine/summary")
    public BalanceSummary summary(@AuthenticationPrincipal AuthenticatedUser user,
                                  @RequestParam(required = false) String currency) {
        return accountService.balanceSummary(user, currency);
    }

    @GetMapping("/{accountNumber}")
    public AccountResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                               @PathVariable String accountNumber) {
        AccountResponse account = accountService.getAccount(accountNumber); // may come from Redis
        if (!user.isAdmin() && !account.ownerUserId().equals(user.userId())) {
            throw new AccountNotFoundException(accountNumber);
        }
        return account;
    }

    @PostMapping("/{accountNumber}/deposits")
    public AccountResponse deposit(@AuthenticationPrincipal AuthenticatedUser user,
                                   @PathVariable String accountNumber,
                                   @Valid @RequestBody MoneyRequest request) {
        return accountService.deposit(user, accountNumber, request.amount());
    }

    // ---------------------------------------------------------------- admin only

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AccountResponse> list(
            @RequestParam(defaultValue = "ACTIVE") AccountStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return accountService.listByStatus(status, pageable);
    }

    @PostMapping("/{accountNumber}/freeze")
    @PreAuthorize("hasRole('ADMIN')")
    public AccountResponse freeze(@PathVariable String accountNumber) {
        return accountService.freeze(accountNumber);
    }

    @PostMapping("/customers/{userId}/freeze")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Integer> freezeCustomer(@PathVariable Long userId) {
        return Map.of("accountsFrozen", accountService.freezeCustomer(userId));
    }
}

package com.payflow.account.api;

import com.payflow.account.api.dto.InternalTransferRequest;
import com.payflow.account.api.dto.TransferResult;
import com.payflow.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service API used by transaction-service through OpenFeign. Not exposed by the gateway. */
@RestController
@RequestMapping("/internal/accounts")
public class InternalAccountController {

    private final AccountService accountService;

    public InternalAccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/transfers")
    public TransferResult applyTransfer(@Valid @RequestBody InternalTransferRequest request) {
        return accountService.applyTransfer(request);
    }
}

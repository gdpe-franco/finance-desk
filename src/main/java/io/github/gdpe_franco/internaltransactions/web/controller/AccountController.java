package io.github.gdpe_franco.internaltransactions.web.controller;

import io.github.gdpe_franco.internaltransactions.application.service.AccountService;
import io.github.gdpe_franco.internaltransactions.application.service.LedgerService;
import io.github.gdpe_franco.internaltransactions.domain.account.AccountCurrency;
import io.github.gdpe_franco.internaltransactions.web.request.CreateAccountRequest;
import io.github.gdpe_franco.internaltransactions.web.request.UpdateAccountRequest;
import io.github.gdpe_franco.internaltransactions.web.response.AccountResponse;
import io.github.gdpe_franco.internaltransactions.web.response.LedgerPageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService accountService;
    private final LedgerService ledgerService;

    public AccountController(AccountService accountService, LedgerService ledgerService) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        var account = accountService.create(request.ownerName(), AccountCurrency.valueOf(request.currency()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(account.id())
                .toUri();
        return ResponseEntity.created(location).body(AccountResponse.from(account));
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable long id) {
        return AccountResponse.from(accountService.get(id));
    }

    @PatchMapping("/{id}")
    public AccountResponse update(@PathVariable long id, @Valid @RequestBody UpdateAccountRequest request) {
        return AccountResponse.from(accountService.update(id, request.ownerName()));
    }

    @GetMapping("/{id}/ledger")
    public LedgerPageResponse ledger(@PathVariable long id,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        return LedgerPageResponse.from(ledgerService.getAccountLedger(id, page, size));
    }
}

package io.github.gdpe_franco.internaltransactions.web.controller;

import io.github.gdpe_franco.internaltransactions.application.service.AccountService;
import io.github.gdpe_franco.internaltransactions.application.service.LedgerService;
import io.github.gdpe_franco.internaltransactions.domain.account.AccountCurrency;
import io.github.gdpe_franco.internaltransactions.web.request.CreateAccountRequest;
import io.github.gdpe_franco.internaltransactions.web.request.UpdateAccountRequest;
import io.github.gdpe_franco.internaltransactions.web.response.AccountResponse;
import io.github.gdpe_franco.internaltransactions.web.response.LedgerPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
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
@Tag(name = "Accounts")
public class AccountController {
    private final AccountService accountService;
    private final LedgerService ledgerService;

    public AccountController(AccountService accountService, LedgerService ledgerService) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
    }

    @PostMapping
    @Operation(summary = "Create an account", description = "Creates a zero-balance ACTIVE account. The server generates its ID and synthetic, non-routable CLABE-format account number.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request; errors maps field names to messages",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        var account = accountService.create(request.ownerName(), AccountCurrency.valueOf(request.currency()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(account.id())
                .toUri();
        return ResponseEntity.created(location).body(AccountResponse.from(account));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an account")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account found",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public AccountResponse get(@Parameter(description = "Internal account ID") @PathVariable long id) {
        return AccountResponse.from(accountService.get(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update account details", description = "Updates the owner name only. Account number, currency, balance, and status are server-controlled.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account updated",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid owner name; errors.ownerName contains the field message",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public AccountResponse update(@Parameter(description = "Internal account ID") @PathVariable long id,
                                  @Valid @RequestBody UpdateAccountRequest request) {
        return AccountResponse.from(accountService.update(id, request.ownerName()));
    }

    @GetMapping("/{id}/ledger")
    @Operation(summary = "List account ledger entries", description = "Returns this account's immutable entries, newest first with a stable ID tie-breaker. Page size is limited to 100.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ledger page",
                    content = @Content(schema = @Schema(implementation = LedgerPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid page or size",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public LedgerPageResponse ledger(@Parameter(description = "Internal account ID") @PathVariable long id,
                                     @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
                                     @Parameter(description = "Entries per page (1–100)") @RequestParam(defaultValue = "50") int size) {
        return LedgerPageResponse.from(ledgerService.getAccountLedger(id, page, size));
    }
}

package io.github.gdpe_franco.internaltransactions.web.controller;

import io.github.gdpe_franco.internaltransactions.application.service.TransferApplicationService;
import io.github.gdpe_franco.internaltransactions.domain.account.AccountCurrency;
import io.github.gdpe_franco.internaltransactions.domain.transfer.TransferRequest;
import io.github.gdpe_franco.internaltransactions.domain.transfer.TransferStatus;
import io.github.gdpe_franco.internaltransactions.web.request.CreateTransferRequest;
import io.github.gdpe_franco.internaltransactions.web.response.TransferResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers")
public class TransferController {
    private final TransferApplicationService transferService;

    public TransferController(TransferApplicationService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @Operation(summary = "Submit a transfer", description = "Registers and processes an internal transfer atomically. Repeating the same normalized request with the same key replays its stored result; a changed request with that key returns 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "New transfer completed",
                    content = @Content(schema = @Schema(implementation = TransferResponse.class))),
            @ApiResponse(responseCode = "200", description = "Stored transfer result replayed",
                    content = @Content(schema = @Schema(implementation = TransferResponse.class))),
            @ApiResponse(responseCode = "400", description = "Malformed request, invalid fields (errors map), or missing/invalid idempotency key",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Idempotency key reused with different transfer details",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Transfer rejected by a business rule. Problem properties include failureCode and transferId.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected registration or processing error; retry with the same key",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Retryable MySQL lock/deadlock error; problem code is RETRYABLE_CONCURRENCY_FAILURE",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<?> create(
            @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true,
                    description = "UUID that identifies this transfer request across retries")
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request) {
        var submission = transferService.submit(new TransferRequest(idempotencyKey, request.sourceAccountId(),
                request.destinationAccountId(), request.amount(), AccountCurrency.valueOf(request.currency())));
        var transfer = submission.transfer();
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(transfer.id())
                .toUri();

        if (transfer.status() == TransferStatus.FAILED) {
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Transfer rejected: " + transfer.failureCode());
            problem.setTitle("Transfer rejected");
            problem.setProperty("failureCode", transfer.failureCode());
            problem.setProperty("transferId", transfer.id());
            return ResponseEntity.unprocessableEntity().location(location).body(problem);
        }
        if (submission.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true")
                    .location(location).body(TransferResponse.from(transfer));
        }
        return ResponseEntity.created(location).body(TransferResponse.from(transfer));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a transfer")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer found",
                    content = @Content(schema = @Schema(implementation = TransferResponse.class))),
            @ApiResponse(responseCode = "404", description = "Transfer not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public TransferResponse get(@Parameter(description = "Internal transfer ID") @PathVariable long id) {
        return TransferResponse.from(transferService.get(id));
    }
}

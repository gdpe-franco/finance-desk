package io.github.gdpe_franco.internaltransactions.web.controller;

import io.github.gdpe_franco.internaltransactions.application.service.TransferApplicationService;
import io.github.gdpe_franco.internaltransactions.domain.account.AccountCurrency;
import io.github.gdpe_franco.internaltransactions.domain.transfer.TransferRequest;
import io.github.gdpe_franco.internaltransactions.domain.transfer.TransferStatus;
import io.github.gdpe_franco.internaltransactions.web.request.CreateTransferRequest;
import io.github.gdpe_franco.internaltransactions.web.response.TransferResponse;
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
public class TransferController {
    private final TransferApplicationService transferService;

    public TransferController(TransferApplicationService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<?> create(
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
    public TransferResponse get(@PathVariable long id) {
        return TransferResponse.from(transferService.get(id));
    }
}

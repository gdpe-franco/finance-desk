package io.github.gdpe_franco.internaltransactions.web.response;

import io.github.gdpe_franco.internaltransactions.domain.transfer.Transfer;
import io.github.gdpe_franco.internaltransactions.domain.transfer.TransferFailureCode;
import io.github.gdpe_franco.internaltransactions.domain.transfer.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        long id,
        UUID reference,
        long sourceAccountId,
        long destinationAccountId,
        BigDecimal amount,
        String currency,
        TransferStatus status,
        TransferFailureCode failureCode,
        Instant createdAt,
        Instant completedAt) {

    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(transfer.id(), transfer.reference(), transfer.sourceAccountId(),
                transfer.destinationAccountId(), transfer.amount(), transfer.currency().name(), transfer.status(),
                transfer.failureCode(), transfer.createdAt(), transfer.completedAt());
    }
}

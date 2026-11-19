package io.github.gdpe_franco.internaltransactions.domain.transfer;

import io.github.gdpe_franco.internaltransactions.domain.account.AccountCurrency;
import io.github.gdpe_franco.internaltransactions.domain.account.Account;
import io.github.gdpe_franco.internaltransactions.domain.account.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Transfer(
        Long id,
        UUID reference,
        UUID idempotencyKey,
        long sourceAccountId,
        long destinationAccountId,
        BigDecimal amount,
        AccountCurrency currency,
        TransferStatus status,
        TransferFailureCode failureCode,
        Instant createdAt,
        Instant completedAt) {

    public Transfer {
        Objects.requireNonNull(reference);
        Objects.requireNonNull(idempotencyKey);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        if (sourceAccountId <= 0 || destinationAccountId <= 0 || amount.scale() > 2 || amount.precision() > 19) {
            throw new IllegalArgumentException("Transfer data is invalid");
        }
        if ((status == TransferStatus.FAILED) != (failureCode != null)
                || (status == TransferStatus.COMPLETED) != (completedAt != null)) {
            throw new IllegalArgumentException("Transfer terminal fields do not match its status");
        }
    }

    public static Transfer pending(TransferRequest request) {
        return new Transfer(null, UUID.randomUUID(), request.idempotencyKey(), request.sourceAccountId(),
                request.destinationAccountId(), request.amount(), request.currency(), TransferStatus.PENDING,
                null, Instant.now(), null);
    }

    public Transfer complete() {
        return new Transfer(id, reference, idempotencyKey, sourceAccountId, destinationAccountId, amount, currency,
                TransferStatus.COMPLETED, null, createdAt, Instant.now());
    }

    public Transfer fail(TransferFailureCode code) {
        return new Transfer(id, reference, idempotencyKey, sourceAccountId, destinationAccountId, amount, currency,
                TransferStatus.FAILED, Objects.requireNonNull(code), createdAt, null);
    }

    public TransferFailureCode failureFor(Account source, Account destination) {
        if (sourceAccountId == destinationAccountId) {
            return TransferFailureCode.SAME_ACCOUNT;
        }
        if (amount.signum() <= 0) {
            return TransferFailureCode.AMOUNT_NOT_POSITIVE;
        }
        if (source == null) {
            return TransferFailureCode.SOURCE_ACCOUNT_NOT_FOUND;
        }
        if (destination == null) {
            return TransferFailureCode.DESTINATION_ACCOUNT_NOT_FOUND;
        }
        if (source.status() != AccountStatus.ACTIVE) {
            return TransferFailureCode.SOURCE_ACCOUNT_INACTIVE;
        }
        if (destination.status() != AccountStatus.ACTIVE) {
            return TransferFailureCode.DESTINATION_ACCOUNT_INACTIVE;
        }
        if (source.currency() != currency || destination.currency() != currency) {
            return TransferFailureCode.CURRENCY_MISMATCH;
        }
        if (source.balance().compareTo(amount) < 0) {
            return TransferFailureCode.INSUFFICIENT_FUNDS;
        }
        if (!destination.canCredit(amount)) {
            return TransferFailureCode.DESTINATION_BALANCE_OVERFLOW;
        }
        return null;
    }
}

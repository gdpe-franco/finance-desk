package io.github.gdpe_franco.internaltransactions.domain.transfer;

import io.github.gdpe_franco.internaltransactions.domain.account.AccountCurrency;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record TransferRequest(
        UUID idempotencyKey,
        long sourceAccountId,
        long destinationAccountId,
        BigDecimal amount,
        AccountCurrency currency) {

    public TransferRequest {
        Objects.requireNonNull(idempotencyKey, "Idempotency key is required");
        Objects.requireNonNull(amount, "Amount is required");
        Objects.requireNonNull(currency, "Currency is required");
        if (sourceAccountId <= 0 || destinationAccountId <= 0) {
            throw new IllegalArgumentException("Account IDs must be positive");
        }
        if (amount.scale() > 2 || amount.precision() > 19) {
            throw new IllegalArgumentException("Amount must fit DECIMAL(19,2)");
        }
    }

    public boolean sameDetails(Transfer transfer) {
        return sourceAccountId == transfer.sourceAccountId()
                && destinationAccountId == transfer.destinationAccountId()
                && amount.compareTo(transfer.amount()) == 0
                && currency == transfer.currency();
    }
}

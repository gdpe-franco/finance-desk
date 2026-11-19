package io.github.gdpe_franco.internaltransactions.domain.ledger;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntry(
        Long id,
        long transferId,
        long accountId,
        LedgerEntryType type,
        BigDecimal amount,
        Instant createdAt) {
}

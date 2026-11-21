package io.github.gdpe_franco.internaltransactions.web.response;

import io.github.gdpe_franco.internaltransactions.application.port.ledger.LedgerPage;
import io.github.gdpe_franco.internaltransactions.domain.ledger.LedgerEntry;
import io.github.gdpe_franco.internaltransactions.domain.ledger.LedgerEntryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record LedgerPageResponse(List<LedgerEntryResponse> entries, PageMetadata page) {

    public record PageMetadata(int number, int size, long totalElements, int totalPages) {
    }

    public record LedgerEntryResponse(
            long id,
            long transferId,
            long accountId,
            LedgerEntryType type,
            BigDecimal amount,
            Instant createdAt) {

        static LedgerEntryResponse from(LedgerEntry entry) {
            return new LedgerEntryResponse(entry.id(), entry.transferId(), entry.accountId(), entry.type(),
                    entry.amount(), entry.createdAt());
        }
    }

    public static LedgerPageResponse from(LedgerPage page) {
        return new LedgerPageResponse(page.entries().stream().map(LedgerEntryResponse::from).toList(),
                new PageMetadata(page.page(), page.size(), page.totalElements(), page.totalPages()));
    }
}

package io.github.gdpe_franco.internaltransactions.application.port.ledger;

import io.github.gdpe_franco.internaltransactions.domain.ledger.LedgerEntry;

import java.util.List;

public record LedgerPage(List<LedgerEntry> entries, int page, int size, boolean hasNext) {
}

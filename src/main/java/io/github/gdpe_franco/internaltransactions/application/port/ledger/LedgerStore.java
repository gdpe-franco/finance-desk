package io.github.gdpe_franco.internaltransactions.application.port.ledger;

import io.github.gdpe_franco.internaltransactions.domain.ledger.LedgerEntry;

import java.util.List;

public interface LedgerStore {
    void saveAll(List<LedgerEntry> entries);

    LedgerPage findByAccountId(long accountId, int page, int size);
}

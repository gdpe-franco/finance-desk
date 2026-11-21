package io.github.gdpe_franco.internaltransactions.infrastructure.persistence.ledger;

import io.github.gdpe_franco.internaltransactions.application.port.ledger.LedgerPage;
import io.github.gdpe_franco.internaltransactions.application.port.ledger.LedgerStore;
import io.github.gdpe_franco.internaltransactions.domain.ledger.LedgerEntry;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class JpaLedgerStore implements LedgerStore {
    private final SpringDataLedgerRepository repository;

    JpaLedgerStore(SpringDataLedgerRepository repository) {
        this.repository = repository;
    }

    @Override
    public void saveAll(List<LedgerEntry> entries) {
        repository.saveAll(entries.stream().map(LedgerEntryEntity::from).toList());
    }

    @Override
    public LedgerPage findByAccountId(long accountId, int page, int size) {
        var result = repository.findByAccountIdOrderByCreatedAtDescIdDesc(accountId, PageRequest.of(page, size));
        return new LedgerPage(result.getContent().stream().map(LedgerEntryEntity::toDomain).toList(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }
}

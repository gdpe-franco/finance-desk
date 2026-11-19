package io.github.gdpe_franco.internaltransactions.infrastructure.persistence.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataLedgerRepository extends JpaRepository<LedgerEntryEntity, Long> {
    Page<LedgerEntryEntity> findByAccountIdOrderByCreatedAtDescIdDesc(long accountId, Pageable pageable);
}

package io.github.gdpe_franco.internaltransactions.infrastructure.persistence.ledger;

import io.github.gdpe_franco.internaltransactions.domain.ledger.LedgerEntry;
import io.github.gdpe_franco.internaltransactions.domain.ledger.LedgerEntryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ledger_entries")
class LedgerEntryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_id", nullable = false)
    private long transferId;

    @Column(name = "account_id", nullable = false)
    private long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private LedgerEntryType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntryEntity() {
    }

    static LedgerEntryEntity from(LedgerEntry entry) {
        var entity = new LedgerEntryEntity();
        entity.transferId = entry.transferId();
        entity.accountId = entry.accountId();
        entity.type = entry.type();
        entity.amount = entry.amount();
        return entity;
    }

    LedgerEntry toDomain() {
        return new LedgerEntry(id, transferId, accountId, type, amount, createdAt);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}

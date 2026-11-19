package io.github.gdpe_franco.internaltransactions.infrastructure.persistence.transfer;

import io.github.gdpe_franco.internaltransactions.domain.account.AccountCurrency;
import io.github.gdpe_franco.internaltransactions.domain.transfer.Transfer;
import io.github.gdpe_franco.internaltransactions.domain.transfer.TransferFailureCode;
import io.github.gdpe_franco.internaltransactions.domain.transfer.TransferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
class TransferEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36, unique = true)
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID reference;

    @Column(name = "idempotency_key", nullable = false, length = 36, unique = true)
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID idempotencyKey;

    @Column(name = "source_account_id", nullable = false)
    private long sourceAccountId;

    @Column(name = "destination_account_id", nullable = false)
    private long destinationAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private AccountCurrency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransferStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 48)
    private TransferFailureCode failureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected TransferEntity() {
    }

    static TransferEntity from(Transfer transfer) {
        var entity = new TransferEntity();
        entity.reference = transfer.reference();
        entity.idempotencyKey = transfer.idempotencyKey();
        entity.sourceAccountId = transfer.sourceAccountId();
        entity.destinationAccountId = transfer.destinationAccountId();
        entity.amount = transfer.amount();
        entity.currency = transfer.currency();
        entity.status = transfer.status();
        entity.failureCode = transfer.failureCode();
        entity.completedAt = transfer.completedAt();
        return entity;
    }

    void updateFrom(Transfer transfer) {
        status = transfer.status();
        failureCode = transfer.failureCode();
        completedAt = transfer.completedAt();
    }

    Transfer toDomain() {
        return new Transfer(id, reference, idempotencyKey, sourceAccountId, destinationAccountId, amount, currency,
                status, failureCode, createdAt, completedAt);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}

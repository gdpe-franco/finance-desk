package io.github.gdpe_franco.internaltransactions.infrastructure.persistence.account;

import io.github.gdpe_franco.internaltransactions.domain.account.Account;
import io.github.gdpe_franco.internaltransactions.domain.account.AccountCurrency;
import io.github.gdpe_franco.internaltransactions.domain.account.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
class AccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false, length = 18, unique = true)
    private String accountNumber;

    @Column(name = "owner_name", nullable = false, length = 120)
    private String ownerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private AccountCurrency currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountEntity() {
    }

    static AccountEntity from(Account account) {
        var entity = new AccountEntity();
        entity.accountNumber = account.accountNumber();
        entity.ownerName = account.ownerName();
        entity.currency = account.currency();
        entity.balance = account.balance();
        entity.status = account.status();
        return entity;
    }

    Account toDomain() {
        return new Account(id, accountNumber, ownerName, currency, balance, status, version, createdAt, updatedAt);
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

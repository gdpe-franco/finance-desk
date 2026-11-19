CREATE TABLE transfers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reference CHAR(36) NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    source_account_id BIGINT NOT NULL,
    destination_account_id BIGINT NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    failure_code VARCHAR(48),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6),
    CONSTRAINT pk_transfers PRIMARY KEY (id),
    CONSTRAINT uq_transfers_reference UNIQUE (reference),
    CONSTRAINT uq_transfers_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_transfers_currency CHECK (currency IN ('MXN', 'USD', 'EUR')),
    CONSTRAINT chk_transfers_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_transfers_terminal_fields CHECK (
        (status = 'PENDING' AND failure_code IS NULL AND completed_at IS NULL) OR
        (status = 'COMPLETED' AND failure_code IS NULL AND completed_at IS NOT NULL) OR
        (status = 'FAILED' AND failure_code IS NOT NULL AND completed_at IS NULL)
    )
);

CREATE TABLE ledger_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    transfer_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    type VARCHAR(8) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ledger_entries PRIMARY KEY (id),
    CONSTRAINT uq_ledger_entries_transfer_type UNIQUE (transfer_id, type),
    CONSTRAINT fk_ledger_entries_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (id),
    CONSTRAINT fk_ledger_entries_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT chk_ledger_entries_type CHECK (type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_entries_amount CHECK (amount > 0),
    INDEX idx_ledger_entries_account_created (account_id, created_at, id)
);

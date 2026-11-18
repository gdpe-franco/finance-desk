CREATE TABLE accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_number VARCHAR(18) NOT NULL,
    owner_name VARCHAR(120) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT uq_accounts_account_number UNIQUE (account_number),
    CONSTRAINT chk_accounts_account_number_format CHECK (account_number REGEXP '^[0-9]{18}$'),
    CONSTRAINT chk_accounts_owner_name CHECK (CHAR_LENGTH(TRIM(owner_name)) BETWEEN 1 AND 120),
    CONSTRAINT chk_accounts_currency CHECK (currency IN ('MXN', 'USD', 'EUR')),
    CONSTRAINT chk_accounts_balance_nonnegative CHECK (balance >= 0),
    CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

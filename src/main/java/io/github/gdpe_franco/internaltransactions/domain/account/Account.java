package io.github.gdpe_franco.internaltransactions.domain.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record Account(
        Long id,
        String accountNumber,
        String ownerName,
        AccountCurrency currency,
        BigDecimal balance,
        AccountStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public Account {
        if (!ClabeAccountNumber.isValid(accountNumber)) {
            throw new IllegalArgumentException("Account number must be an 18-digit CLABE-format value");
        }
        if (ownerName == null || ownerName.isBlank() || ownerName.trim().length() > 120) {
            throw new IllegalArgumentException("Owner name must contain 1 to 120 characters");
        }
        ownerName = ownerName.trim();
        Objects.requireNonNull(currency, "Currency is required");
        Objects.requireNonNull(status, "Status is required");
        Objects.requireNonNull(balance, "Balance is required");
        if (balance.signum() < 0 || balance.scale() > 2 || balance.precision() > 19) {
            throw new IllegalArgumentException("Balance must be nonnegative and fit DECIMAL(19,2)");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Version cannot be negative");
        }
        if ((createdAt == null) != (updatedAt == null)) {
            throw new IllegalArgumentException("Created and updated timestamps must both be set or both be absent");
        }
    }

    public static Account open(String ownerName, AccountCurrency currency) {
        return new Account(null, ClabeAccountNumber.generate(), ownerName, currency,
                new BigDecimal("0.00"), AccountStatus.ACTIVE, 0, null, null);
    }
}

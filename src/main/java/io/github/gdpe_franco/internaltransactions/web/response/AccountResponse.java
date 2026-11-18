package io.github.gdpe_franco.internaltransactions.web.response;

import io.github.gdpe_franco.internaltransactions.domain.account.Account;
import io.github.gdpe_franco.internaltransactions.domain.account.AccountCurrency;
import io.github.gdpe_franco.internaltransactions.domain.account.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        long id,
        String accountNumber,
        String ownerName,
        AccountCurrency currency,
        BigDecimal balance,
        AccountStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.id(), account.accountNumber(), account.ownerName(), account.currency(),
                account.balance(), account.status(), account.version(), account.createdAt(), account.updatedAt());
    }
}

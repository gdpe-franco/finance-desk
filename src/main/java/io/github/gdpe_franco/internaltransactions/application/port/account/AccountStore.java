package io.github.gdpe_franco.internaltransactions.application.port.account;

import io.github.gdpe_franco.internaltransactions.domain.account.Account;

import java.util.Optional;

public interface AccountStore {
    Account save(Account account);

    Optional<Account> findById(long id);
}

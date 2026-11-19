package io.github.gdpe_franco.internaltransactions.application.service;

import io.github.gdpe_franco.internaltransactions.application.port.account.AccountStore;
import io.github.gdpe_franco.internaltransactions.domain.account.Account;
import io.github.gdpe_franco.internaltransactions.domain.account.AccountCurrency;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountService {
    private final AccountStore accountStore;

    public AccountService(AccountStore accountStore) {
        this.accountStore = accountStore;
    }

    public Account create(String ownerName, AccountCurrency currency) {
        return accountStore.save(Account.open(ownerName, currency));
    }

    @Transactional(readOnly = true)
    public Account get(long id) {
        return accountStore.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    public Account update(long id, String ownerName) {
        return accountStore.update(id, ownerName).orElseThrow(() -> new AccountNotFoundException(id));
    }
}

package io.github.gdpe_franco.internaltransactions.application.service;

import io.github.gdpe_franco.internaltransactions.application.port.account.AccountStore;
import io.github.gdpe_franco.internaltransactions.application.port.ledger.LedgerPage;
import io.github.gdpe_franco.internaltransactions.application.port.ledger.LedgerStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LedgerService {
    private static final int MAX_PAGE_SIZE = 100;

    private final AccountStore accountStore;
    private final LedgerStore ledgerStore;

    public LedgerService(AccountStore accountStore, LedgerStore ledgerStore) {
        this.accountStore = accountStore;
        this.ledgerStore = ledgerStore;
    }

    public LedgerPage getAccountLedger(long accountId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidLedgerPageException();
        }
        accountStore.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        return ledgerStore.findByAccountId(accountId, page, size);
    }
}

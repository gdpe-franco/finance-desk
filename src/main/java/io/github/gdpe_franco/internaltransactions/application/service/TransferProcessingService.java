package io.github.gdpe_franco.internaltransactions.application.service;

import io.github.gdpe_franco.internaltransactions.application.port.account.AccountStore;
import io.github.gdpe_franco.internaltransactions.application.port.ledger.LedgerStore;
import io.github.gdpe_franco.internaltransactions.application.port.transfer.TransferStore;
import io.github.gdpe_franco.internaltransactions.domain.account.Account;
import io.github.gdpe_franco.internaltransactions.domain.ledger.LedgerEntry;
import io.github.gdpe_franco.internaltransactions.domain.ledger.LedgerEntryType;
import io.github.gdpe_franco.internaltransactions.domain.transfer.Transfer;
import io.github.gdpe_franco.internaltransactions.domain.transfer.TransferStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TransferProcessingService {
    private final AccountStore accountStore;
    private final TransferStore transferStore;
    private final LedgerStore ledgerStore;

    public TransferProcessingService(AccountStore accountStore, TransferStore transferStore, LedgerStore ledgerStore) {
        this.accountStore = accountStore;
        this.transferStore = transferStore;
        this.ledgerStore = ledgerStore;
    }

    @Transactional
    public Transfer process(long transferId) {
        var transfer = transferStore.findByIdForUpdate(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));
        if (transfer.status() != TransferStatus.PENDING) {
            return transfer;
        }

        var accountsById = lockAccounts(transfer);
        var source = accountsById.get(transfer.sourceAccountId());
        var destination = accountsById.get(transfer.destinationAccountId());
        var failure = transfer.failureFor(source, destination);
        if (failure != null) {
            return transferStore.save(transfer.fail(failure));
        }

        accountStore.save(source.debit(transfer.amount()));
        accountStore.save(destination.credit(transfer.amount()));
        ledgerStore.saveAll(List.of(
                new LedgerEntry(null, transfer.id(), source.id(), LedgerEntryType.DEBIT, transfer.amount(), null),
                new LedgerEntry(null, transfer.id(), destination.id(), LedgerEntryType.CREDIT, transfer.amount(), null)));
        return transferStore.save(transfer.complete());
    }

    private Map<Long, Account> lockAccounts(Transfer transfer) {
        var locked = accountStore.findByIdsForUpdate(
                List.of(transfer.sourceAccountId(), transfer.destinationAccountId()));
        var accountsById = new HashMap<Long, Account>();
        locked.forEach(account -> accountsById.put(account.id(), account));
        return accountsById;
    }
}

package io.github.gdpe_franco.internaltransactions.infrastructure.persistence.account;

import io.github.gdpe_franco.internaltransactions.application.port.account.AccountStore;
import io.github.gdpe_franco.internaltransactions.domain.account.Account;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
class JpaAccountStore implements AccountStore {
    private final SpringDataAccountRepository repository;

    JpaAccountStore(SpringDataAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public Account save(Account account) {
        if (account.id() == null) {
            return repository.save(AccountEntity.from(account)).toDomain();
        }
        var entity = repository.findById(account.id()).orElseThrow();
        entity.updateFrom(account);
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<Account> findById(long id) {
        return repository.findById(id).map(AccountEntity::toDomain);
    }

    @Override
    public Optional<Account> update(long id, String ownerName) {
        return repository.findById(id).map(entity -> {
            entity.updateOwnerName(entity.toDomain().withOwnerName(ownerName).ownerName());
            return repository.saveAndFlush(entity).toDomain();
        });
    }

    @Override
    public List<Account> findByIdsForUpdate(List<Long> ids) {
        var accounts = new ArrayList<Account>();
        for (var id : ids.stream().distinct().sorted().toList()) {
            repository.findByIdForUpdate(id).map(AccountEntity::toDomain).ifPresent(accounts::add);
        }
        return accounts;
    }
}

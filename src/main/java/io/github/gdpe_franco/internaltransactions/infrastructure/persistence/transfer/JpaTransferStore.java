package io.github.gdpe_franco.internaltransactions.infrastructure.persistence.transfer;

import io.github.gdpe_franco.internaltransactions.application.port.transfer.TransferStore;
import io.github.gdpe_franco.internaltransactions.domain.transfer.Transfer;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaTransferStore implements TransferStore {
    private final SpringDataTransferRepository repository;

    JpaTransferStore(SpringDataTransferRepository repository) {
        this.repository = repository;
    }

    @Override
    public Transfer save(Transfer transfer) {
        if (transfer.id() == null) {
            return repository.save(TransferEntity.from(transfer)).toDomain();
        }
        var entity = repository.findById(transfer.id()).orElseThrow();
        entity.updateFrom(transfer);
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Transfer> findById(long id) {
        return repository.findById(id).map(TransferEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Transfer> findByIdempotencyKey(UUID idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey).map(TransferEntity::toDomain);
    }

    @Override
    public Optional<Transfer> findByIdForUpdate(long id) {
        return repository.findByIdForUpdate(id).map(TransferEntity::toDomain);
    }
}

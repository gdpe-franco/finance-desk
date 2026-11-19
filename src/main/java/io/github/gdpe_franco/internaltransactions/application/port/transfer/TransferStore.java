package io.github.gdpe_franco.internaltransactions.application.port.transfer;

import io.github.gdpe_franco.internaltransactions.domain.transfer.Transfer;

import java.util.Optional;
import java.util.UUID;

public interface TransferStore {
    Transfer save(Transfer transfer);

    Optional<Transfer> findById(long id);

    Optional<Transfer> findByIdempotencyKey(UUID idempotencyKey);

    Optional<Transfer> findByIdForUpdate(long id);
}

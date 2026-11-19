package io.github.gdpe_franco.internaltransactions.infrastructure.persistence.transfer;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface SpringDataTransferRepository extends JpaRepository<TransferEntity, Long> {
    Optional<TransferEntity> findByIdempotencyKey(UUID idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transfer from TransferEntity transfer where transfer.id = :id")
    Optional<TransferEntity> findByIdForUpdate(@Param("id") long id);
}

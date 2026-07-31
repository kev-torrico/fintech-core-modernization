package com.finbank.transfers.infrastructure;

import com.finbank.transfers.domain.Transfers;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransfersRepository extends JpaRepository<Transfers, UUID> {
    List<Transfers> findBySourceAccountIdOrTargetAccountIdOrderByCreatedAtDesc(
        UUID sourceAccountId, UUID targetAccountId);

    Optional<Transfers> findByIdempotencyKey(String idempotencyKey);
}

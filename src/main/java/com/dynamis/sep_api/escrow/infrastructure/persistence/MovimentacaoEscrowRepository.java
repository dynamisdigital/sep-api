package com.dynamis.sep_api.escrow.infrastructure.persistence;

import com.dynamis.sep_api.escrow.domain.model.MovimentacaoEscrow;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface MovimentacaoEscrowRepository extends JpaRepository<MovimentacaoEscrow, UUID> {

    Optional<MovimentacaoEscrow> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MovimentacaoEscrow m where m.id = :id")
    Optional<MovimentacaoEscrow> findByIdForUpdate(UUID id);
}

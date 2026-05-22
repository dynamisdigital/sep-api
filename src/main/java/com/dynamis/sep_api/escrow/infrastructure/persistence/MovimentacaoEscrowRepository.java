package com.dynamis.sep_api.escrow.infrastructure.persistence;

import com.dynamis.sep_api.escrow.domain.model.MovimentacaoEscrow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MovimentacaoEscrowRepository extends JpaRepository<MovimentacaoEscrow, UUID> {

    Optional<MovimentacaoEscrow> findByIdempotencyKey(String idempotencyKey);
}

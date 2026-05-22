package com.dynamis.sep_api.escrow.infrastructure.persistence;

import com.dynamis.sep_api.escrow.domain.model.ContaEscrow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ContaEscrowRepository extends JpaRepository<ContaEscrow, UUID> {

    Optional<ContaEscrow> findFirstByTitular(String titular);
}

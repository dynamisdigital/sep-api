package com.dynamis.sep_api.credito.infrastructure.persistence;

import com.dynamis.sep_api.credito.domain.model.DecisaoCredito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DecisaoCreditoRepository extends JpaRepository<DecisaoCredito, UUID> {

    Optional<DecisaoCredito> findByPropostaId(UUID propostaId);
}

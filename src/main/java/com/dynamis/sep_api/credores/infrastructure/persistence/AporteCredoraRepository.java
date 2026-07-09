package com.dynamis.sep_api.credores.infrastructure.persistence;

import com.dynamis.sep_api.credores.domain.model.AporteCredora;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AporteCredoraRepository extends JpaRepository<AporteCredora, UUID> {

    Optional<AporteCredora> findByOperacaoIdAndIdempotencyKey(UUID operacaoId, String idempotencyKey);

    List<AporteCredora> findByOperacaoIdOrderByDataCriacaoDesc(UUID operacaoId);

    Optional<AporteCredora> findByReferenciaEscrow(String referenciaEscrow);
}

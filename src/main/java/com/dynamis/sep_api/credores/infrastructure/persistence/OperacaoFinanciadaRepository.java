package com.dynamis.sep_api.credores.infrastructure.persistence;

import com.dynamis.sep_api.credores.domain.model.OperacaoFinanciada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OperacaoFinanciadaRepository extends JpaRepository<OperacaoFinanciada, UUID> {

    List<OperacaoFinanciada> findByEmpresaCredoraIdOrderByDataCriacaoDesc(UUID empresaCredoraId);

    Optional<OperacaoFinanciada> findByIdAndEmpresaCredoraId(UUID id, UUID empresaCredoraId);

    boolean existsByEmpresaCredoraIdAndContratoId(UUID empresaCredoraId, UUID contratoId);
}

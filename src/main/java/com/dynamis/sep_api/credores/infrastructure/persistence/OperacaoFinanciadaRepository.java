package com.dynamis.sep_api.credores.infrastructure.persistence;

import com.dynamis.sep_api.credores.domain.model.OperacaoFinanciada;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OperacaoFinanciadaRepository extends JpaRepository<OperacaoFinanciada, UUID> {

    List<OperacaoFinanciada> findByEmpresaCredoraIdOrderByDataCriacaoDesc(UUID empresaCredoraId);

    Optional<OperacaoFinanciada> findByIdAndEmpresaCredoraId(UUID id, UUID empresaCredoraId);

    boolean existsByEmpresaCredoraIdAndContratoId(UUID empresaCredoraId, UUID contratoId);

    /**
     * Leitura com {@code SELECT FOR UPDATE} para serializar registros de aporte concorrentes na
     * mesma operacao (Sprint 29): o check de idempotencia por operacao+chave roda sob o lock, e a
     * requisicao concorrente enxerga o aporte ja criado (replay 200) em vez de estourar o UNIQUE.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OperacaoFinanciada o where o.id = :id")
    Optional<OperacaoFinanciada> findByIdForUpdate(UUID id);
}

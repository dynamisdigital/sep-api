package com.dynamis.sep_api.cobranca.infrastructure.persistence;

import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AgendaPagamentoRepository extends JpaRepository<AgendaPagamento, UUID> {

    /**
     * Sprint 13 Task 13.6: contrato pode ter multiplas agendas no historico (renegociacoes); a
     * UNIQUE parcial WHERE ativa garante uma unica vigente. {@code findByContratoIdAndAtivaTrue}
     * eh a busca operacional default.
     */
    Optional<AgendaPagamento> findByContratoIdAndAtivaTrue(UUID contratoId);

    @Deprecated
    default Optional<AgendaPagamento> findByContratoId(UUID contratoId) {
        return findByContratoIdAndAtivaTrue(contratoId);
    }

    boolean existsByContratoIdAndAtivaTrue(UUID contratoId);

    @Deprecated
    default boolean existsByContratoId(UUID contratoId) {
        return existsByContratoIdAndAtivaTrue(contratoId);
    }
}

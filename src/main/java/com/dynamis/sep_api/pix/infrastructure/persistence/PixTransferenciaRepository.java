package com.dynamis.sep_api.pix.infrastructure.persistence;

import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PixTransferenciaRepository extends JpaRepository<PixTransferencia, UUID> {

    Optional<PixTransferencia> findByIdempotencyKey(String idempotencyKey);

    /**
     * Transferencia mais recente do contrato em algum dos estados informados. Usado na Sprint 20
     * para bloquear novo desembolso enquanto houver um que "ocupa" o contrato
     * (CRIADA/SOLICITADA/PROCESSANDO/CONCLUIDA).
     */
    Optional<PixTransferencia> findFirstByContratoIdAndStatusInOrderByDataCriacaoDesc(
            UUID contratoId, Collection<StatusPixTransferencia> status);
}

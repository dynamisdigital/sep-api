package com.dynamis.sep_api.credores.web.dto;

import com.dynamis.sep_api.credores.application.dto.AporteCredoraView;
import com.dynamis.sep_api.credores.domain.vo.StatusAporteCredora;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Representacao REST publica do aporte da credora (Sprint 29). Contrato minimo — nunca expoe
 * {@code idempotencyKey}, {@code referenciaEscrow}, motivo tecnico de falha, conta, provider ou
 * payload bancario.
 */
public record AporteCredoraResponse(
        UUID id,
        UUID operacaoId,
        StatusAporteCredora status,
        BigDecimal valor,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataAtualizacao) {

    public static AporteCredoraResponse de(AporteCredoraView view) {
        return new AporteCredoraResponse(
                view.id(), view.operacaoId(), view.status(), view.valor(), view.dataCriacao(), view.dataAtualizacao());
    }
}

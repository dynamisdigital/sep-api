package com.dynamis.sep_api.credores.application.dto;

import com.dynamis.sep_api.credores.domain.model.AporteCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusAporteCredora;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Projecao publica minima do aporte da credora (Sprint 29). Somente campos do contrato REST —
 * nunca {@code idempotencyKey}, {@code referenciaEscrow}, motivo de falha ou dado de
 * escrow/provider.
 */
public record AporteCredoraView(
        UUID id,
        UUID operacaoId,
        StatusAporteCredora status,
        BigDecimal valor,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataAtualizacao) {

    public static AporteCredoraView de(AporteCredora aporte) {
        return new AporteCredoraView(
                aporte.getId(),
                aporte.getOperacaoId(),
                aporte.getStatus(),
                aporte.getValor(),
                aporte.getDataCriacao(),
                aporte.getDataModificacao());
    }
}

package com.dynamis.sep_api.credores.web.dto;

import com.dynamis.sep_api.credores.application.dto.PixOperacaoStatusView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Resposta publica do status Pix de uma operacao da carteira da credora (Sprint 26 — Gate P3).
 * Exposicao minima: status publico, valor e instante de atualizacao. Sem tomador, contrato, chave
 * Pix, IDs internos, escrow ou detalhe operacional.
 */
public record PixOperacaoCredoraResponse(
        @Schema(description = "Status publico do desembolso Pix da operacao", example = "LIQUIDADO") String status,
        @Schema(description = "Valor do desembolso", example = "1500.00") BigDecimal valor,
        @Schema(description = "Instante da ultima atualizacao do status") OffsetDateTime atualizadoEm) {

    public static PixOperacaoCredoraResponse from(PixOperacaoStatusView view) {
        return new PixOperacaoCredoraResponse(view.status(), view.valor(), view.atualizadoEm());
    }
}

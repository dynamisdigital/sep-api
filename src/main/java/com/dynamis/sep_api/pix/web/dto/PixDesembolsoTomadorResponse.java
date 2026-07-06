package com.dynamis.sep_api.pix.web.dto;

import com.dynamis.sep_api.pix.application.dto.PixDesembolsoTomadorResult;
import com.dynamis.sep_api.pix.domain.vo.StatusPixPublico;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Resposta publica do status de desembolso Pix do tomador (Sprint 26 — Gate P1). Exposicao minima:
 * status publico, valor e instante de atualizacao. Sem chave Pix, txid, endToEndId, IDs internos,
 * provider ou escrow.
 */
public record PixDesembolsoTomadorResponse(
        @Schema(description = "Status publico do desembolso Pix", example = "EM_PROCESSAMENTO") StatusPixPublico status,
        @Schema(description = "Valor do desembolso", example = "1500.00") BigDecimal valor,
        @Schema(description = "Instante da ultima atualizacao do status") OffsetDateTime atualizadoEm) {

    public static PixDesembolsoTomadorResponse from(PixDesembolsoTomadorResult result) {
        return new PixDesembolsoTomadorResponse(result.status(), result.valor(), result.atualizadoEm());
    }
}

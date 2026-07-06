package com.dynamis.sep_api.pix.web.dto;

import com.dynamis.sep_api.pix.application.dto.PixPagamentoParcelaResult;
import com.dynamis.sep_api.pix.domain.vo.StatusPixParcelaPublico;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Resposta publica do estado Pix de uma parcela do tomador (Sprint 26 — Gate P2). Exposicao minima:
 * status publico, valor esperado, instante de atualizacao e mensagem publica opcional. Sem txid,
 * copia-cola, endToEndId, motivo tecnico, IDs internos, provider ou escrow.
 */
public record PixPagamentoParcelaResponse(
        @Schema(description = "Status publico do pagamento Pix da parcela", example = "AGUARDANDO")
                StatusPixParcelaPublico status,
        @Schema(description = "Valor esperado da referencia Pix", example = "350.00") BigDecimal valor,
        @Schema(description = "Instante da ultima atualizacao da fonte do status") OffsetDateTime atualizadoEm,
        @Schema(description = "Mensagem publica sanitizada (apenas em estados de atencao)", nullable = true)
                String mensagemPublica) {

    public static PixPagamentoParcelaResponse from(PixPagamentoParcelaResult result) {
        return new PixPagamentoParcelaResponse(
                result.status(), result.valor(), result.atualizadoEm(), result.mensagemPublica());
    }
}

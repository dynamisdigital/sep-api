package com.dynamis.sep_api.pix.web.dto;

import com.dynamis.sep_api.pix.application.dto.RecebimentoPixResult;
import com.dynamis.sep_api.pix.domain.vo.StatusPixRecebimento;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resposta de recebimento Pix para operacao assistida (Sprint 21 Task 21.6). Exposta apenas a papeis
 * internos. Nunca expoe payload bruto nem chave Pix.
 */
@Schema(description = "Recebimento Pix conciliado/divergente")
public record RecebimentoPixResponse(
        UUID recebimentoId,
        StatusPixRecebimento status,
        BigDecimal valor,
        @Schema(description = "Identificador tecnico do arranjo Pix (end-to-end id)") String endToEndId,
        UUID referenciaId,
        UUID parcelaId,
        @Schema(description = "Recebimento de cobranca gerado pela baixa, quando conciliado")
                UUID recebimentoCobrancaId,
        String motivoDivergencia,
        OffsetDateTime recebidoEm) {

    public static RecebimentoPixResponse de(RecebimentoPixResult r) {
        return new RecebimentoPixResponse(
                r.recebimentoId(),
                r.status(),
                r.valor(),
                r.endToEndId(),
                r.referenciaId(),
                r.parcelaId(),
                r.recebimentoCobrancaId(),
                r.motivoDivergencia(),
                r.recebidoEm());
    }
}

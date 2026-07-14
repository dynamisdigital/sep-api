package com.dynamis.sep_api.pix.web.dto;

import com.dynamis.sep_api.pix.application.dto.CadastrarChavePixResult;
import com.dynamis.sep_api.pix.application.dto.ChavePixItemResult;
import com.dynamis.sep_api.pix.domain.vo.StatusChavePix;
import com.dynamis.sep_api.pix.domain.vo.TipoChavePix;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resposta de cadastro/listagem de chave Pix da conta operacional (Sprint 31 Task 31.7). Sempre
 * mascarada — nunca expoe valor em claro, hash, provider id ou idempotency key.
 */
@Schema(description = "Chave Pix da conta operacional (sempre mascarada)")
public record ChavePixResponse(
        UUID id,
        TipoChavePix tipo,
        @Schema(description = "Mascara segura da chave; o valor integral nunca e retornado") String valorMascarado,
        StatusChavePix status,
        OffsetDateTime criadaEm,
        OffsetDateTime removidaEm) {

    public static ChavePixResponse de(CadastrarChavePixResult r) {
        return new ChavePixResponse(r.id(), r.tipo(), r.valorMascarado(), r.status(), r.criadaEm(), r.removidaEm());
    }

    public static ChavePixResponse de(ChavePixItemResult r) {
        return new ChavePixResponse(r.id(), r.tipo(), r.valorMascarado(), r.status(), r.criadaEm(), r.removidaEm());
    }
}

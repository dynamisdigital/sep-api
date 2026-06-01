package com.dynamis.sep_api.pix.web.dto;

import com.dynamis.sep_api.pix.application.dto.SolicitarDesembolsoPixResult;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/** Resposta da solicitacao de desembolso Pix (Sprint 20 Task 20.5). Nunca expoe a chave em claro. */
@Schema(description = "Transferencia Pix de desembolso")
public record DesembolsoResponse(
        UUID transferenciaId,
        UUID contratoId,
        StatusPixTransferencia status,
        BigDecimal valor,
        @Schema(description = "Mascara da chave Pix destino") String chaveDestinoMascara,
        @Schema(description = "false quando o retorno eh idempotente (transferencia ja existia)") boolean novo) {

    public static DesembolsoResponse de(SolicitarDesembolsoPixResult r) {
        return new DesembolsoResponse(
                r.transferenciaId(), r.contratoId(), r.status(), r.valor(), r.chaveDestinoMascara(), r.novo());
    }
}

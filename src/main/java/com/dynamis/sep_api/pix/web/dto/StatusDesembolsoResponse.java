package com.dynamis.sep_api.pix.web.dto;

import com.dynamis.sep_api.pix.application.dto.StatusDesembolsoPixResult;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/** Resposta de consulta de status de desembolso Pix (Sprint 20 Task 20.5). */
@Schema(description = "Status atual de um desembolso Pix")
public record StatusDesembolsoResponse(
        UUID transferenciaId,
        UUID contratoId,
        StatusPixTransferencia status,
        BigDecimal valor,
        @Schema(description = "Mascara da chave Pix destino") String chaveDestinoMascara,
        @Schema(description = "true quando o provider foi consultado mas falhou (status local devolvido)")
                boolean providerIndisponivel) {

    public static StatusDesembolsoResponse de(StatusDesembolsoPixResult r) {
        return new StatusDesembolsoResponse(
                r.transferenciaId(),
                r.contratoId(),
                r.status(),
                r.valor(),
                r.chaveDestinoMascara(),
                r.providerIndisponivel());
    }
}

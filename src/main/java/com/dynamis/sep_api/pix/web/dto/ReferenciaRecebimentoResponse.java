package com.dynamis.sep_api.pix.web.dto;

import com.dynamis.sep_api.pix.application.dto.GerarReferenciaRecebimentoPixResult;
import com.dynamis.sep_api.pix.application.dto.ReferenciaRecebimentoPixResult;
import com.dynamis.sep_api.pix.domain.vo.StatusPixReferenciaRecebimento;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resposta de referencia Pix de recebimento (Sprint 21 Task 21.6). So dados de pagamento nao
 * sensiveis. {@code novo} indica se a referencia foi criada agora (true) ou reaproveitada/consultada
 * (false).
 */
@Schema(description = "Referencia Pix de recebimento de uma parcela")
public record ReferenciaRecebimentoResponse(
        UUID referenciaId,
        UUID parcelaId,
        String txid,
        @Schema(description = "Pix copia-cola para pagamento") String codigoCopiaCola,
        BigDecimal valorEsperado,
        StatusPixReferenciaRecebimento status,
        boolean novo) {

    public static ReferenciaRecebimentoResponse de(GerarReferenciaRecebimentoPixResult r) {
        return new ReferenciaRecebimentoResponse(
                r.referenciaId(),
                r.parcelaId(),
                r.txid(),
                r.codigoCopiaCola(),
                r.valorEsperado(),
                r.status(),
                r.novo());
    }

    public static ReferenciaRecebimentoResponse de(ReferenciaRecebimentoPixResult r) {
        return new ReferenciaRecebimentoResponse(
                r.referenciaId(), r.parcelaId(), r.txid(), r.codigoCopiaCola(), r.valorEsperado(), r.status(), false);
    }
}

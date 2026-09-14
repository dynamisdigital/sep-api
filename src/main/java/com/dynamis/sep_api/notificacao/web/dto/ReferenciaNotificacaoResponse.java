package com.dynamis.sep_api.notificacao.web.dto;

import com.dynamis.sep_api.notificacao.domain.vo.Referencia;
import com.dynamis.sep_api.notificacao.domain.vo.TipoReferencia;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Recurso ao qual a notificacao aponta, para o cliente montar o link (ADR 0021 §7). */
@Schema(name = "ReferenciaNotificacaoResponse")
public record ReferenciaNotificacaoResponse(
        @Schema(
                        description = "Tipo do recurso referenciado",
                        example = "CONTRATO",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                TipoReferencia tipo,
        @Schema(
                        description = "Id do recurso referenciado",
                        example = "1f0a8c2e-7d3b-6e10-9a4f-2b7c5d8e9f01",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                UUID id) {

    static ReferenciaNotificacaoResponse from(Referencia referencia) {
        return referencia == null ? null : new ReferenciaNotificacaoResponse(referencia.tipo(), referencia.id());
    }
}

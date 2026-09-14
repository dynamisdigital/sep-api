package com.dynamis.sep_api.notificacao.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Contador da central: quantas notificacoes do usuario ainda nao foram lidas (ADR 0021 §9). */
@Schema(name = "NotificacoesNaoLidasResponse")
public record NotificacoesNaoLidasResponse(
        @Schema(description = "Notificacoes nao lidas", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
                long naoLidas) {}

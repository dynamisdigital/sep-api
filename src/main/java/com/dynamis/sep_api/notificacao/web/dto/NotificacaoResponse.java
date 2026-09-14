package com.dynamis.sep_api.notificacao.web.dto;

import com.dynamis.sep_api.notificacao.domain.model.Notificacao;
import com.dynamis.sep_api.notificacao.domain.vo.TipoNotificacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Item da central (ADR 0021 §9). Exposicao minima: nao carrega usuario, origem, canal nem situacao de
 * entrega — a central e sempre {@code IN_APP} do proprio usuario, e esses campos so serviriam para
 * vazar detalhe interno.
 *
 * <p>{@code lidaEm} e {@code referencia} vao sempre no JSON, com {@code null} quando vazios, mas nao
 * sao declarados {@code required}: em OpenAPI 3.1 o springdoc descarta {@code nullable = true}, e
 * declara-los obrigatorios publicaria "string nao nula" para um campo que chega nulo.
 */
@Schema(name = "NotificacaoResponse")
public record NotificacaoResponse(
        @Schema(
                        description = "Id da notificacao, usado para marcar como lida",
                        example = "1f0a8c2e-7d3b-6e10-9a4f-2b7c5d8e9f00",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                UUID id,
        @Schema(
                        description = "Fato que originou a notificacao",
                        example = "DESEMBOLSO_PIX_CONCLUIDO",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                TipoNotificacao tipo,
        @Schema(
                        description = "Titulo curto, pronto para exibir",
                        example = "Desembolso concluido",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String titulo,
        @Schema(
                        description = "Mensagem pronta para exibir",
                        example = "A transferencia Pix do desembolso do seu contrato foi concluida.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String mensagem,
        @Schema(description = "Instante em que a notificacao foi criada", requiredMode = Schema.RequiredMode.REQUIRED)
                OffsetDateTime criadaEm,
        @Schema(description = "Instante da primeira leitura; presente e nulo enquanto nao lida") OffsetDateTime lidaEm,
        @Schema(description = "Recurso relacionado; presente e nulo quando o tipo nao aponta recurso")
                ReferenciaNotificacaoResponse referencia) {

    public static NotificacaoResponse from(Notificacao notificacao) {
        return new NotificacaoResponse(
                notificacao.getId(),
                notificacao.getOrigem().tipo(),
                notificacao.getConteudo().titulo(),
                notificacao.getConteudo().mensagem(),
                notificacao.getCriadaEm(),
                notificacao.getLidaEm().orElse(null),
                ReferenciaNotificacaoResponse.from(notificacao.getConteudo().referencia()));
    }
}

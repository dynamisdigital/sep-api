package com.dynamis.sep_api.cobranca.application.port.out.dto;

import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;

import java.util.Map;
import java.util.Objects;

/**
 * Mensagem transacional a enviar (Sprint 13 - ADR 0014).
 *
 * <p>Sem dado pessoal direto: o use case carrega apenas o destinatario tecnico (email/telefone),
 * o nome do template e as variaveis ja sanitizadas. O adapter eh responsavel por renderizar o
 * conteudo final via {@link com.dynamis.sep_api.cobranca.application.port.out.TemplateNotificacaoEngine}
 * — port nao transporta payload renderizado pra preservar separacao.
 *
 * <p>{@code correlationId} eh propagado para tracing distribuido (MDC).
 */
public record Notificacao(
        CanalNotificacao canal,
        String destinatario,
        String template,
        Map<String, Object> variaveis,
        String correlationId) {

    public Notificacao {
        Objects.requireNonNull(canal, "canal obrigatorio");
        if (destinatario == null || destinatario.isBlank()) {
            throw new IllegalArgumentException("destinatario obrigatorio");
        }
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("template obrigatorio");
        }
        Objects.requireNonNull(variaveis, "variaveis obrigatorio (use Map.of())");
        variaveis = Map.copyOf(variaveis);
    }
}

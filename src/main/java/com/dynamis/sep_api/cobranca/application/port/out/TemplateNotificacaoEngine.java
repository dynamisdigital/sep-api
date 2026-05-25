package com.dynamis.sep_api.cobranca.application.port.out;

import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;

import java.util.Map;

/**
 * Renderizador de templates de notificacao (Sprint 13 - ADR 0014).
 *
 * <p>O canal define o modo de renderizacao: {@link CanalNotificacao#EMAIL} usa templates HTML
 * (extensao {@code .html}), {@link CanalNotificacao#SMS} usa templates texto plano (extensao
 * {@code .txt}). Templates vivem em {@code src/main/resources/templates/notificacoes/}.
 */
public interface TemplateNotificacaoEngine {

    String renderizar(CanalNotificacao canal, String template, Map<String, Object> variaveis);
}

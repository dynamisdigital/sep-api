package com.dynamis.sep_api.notificacao.domain.vo;

/**
 * Situacao de entrega de uma notificacao, separada da leitura (ADR 0021 §3).
 *
 * <p>{@code IN_APP} nasce e fica {@code DISPONIVEL}. {@code EMAIL} nasce {@code PENDENTE} e termina
 * em {@code ENVIADA} (provider real aceitou — nao e ciencia do usuario), {@code SIMULADA} (adapter
 * de log, nunca comprovante) ou {@code FALHOU}.
 */
public enum SituacaoEntrega {
    DISPONIVEL,
    PENDENTE,
    ENVIADA,
    SIMULADA,
    FALHOU
}

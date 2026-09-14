package com.dynamis.sep_api.notificacao.domain.vo;

/**
 * Fato de negocio que originou a notificacao (ADR 0021 §4). Cada tipo tem uma origem estavel, usada
 * na chave de idempotencia junto com o destinatario.
 */
public enum TipoNotificacao {
    /** Desembolso Pix liquidado; origem = id da transferencia. */
    DESEMBOLSO_PIX_CONCLUIDO,
    /** Conta entrou em lockout; origem = instante do evento de bloqueio. */
    CONTA_BLOQUEADA
}

package com.dynamis.sep_api.notificacao.domain.vo;

/**
 * Canais de notificacao do modulo transversal (Sprint 38, ADR 0021 §3).
 *
 * <p>{@code EMAIL} e {@code SMS} repetem o enum de {@code cobranca.domain.vo}, que permanece ate a
 * regua de cobranca migrar — duplicacao temporaria declarada. {@code IN_APP} e o unico canal da
 * central e nao depende de provider externo. {@code SMS} existe por compatibilidade: nenhum caminho
 * deste modulo o entrega.
 */
public enum CanalNotificacao {
    EMAIL,
    SMS,
    IN_APP
}

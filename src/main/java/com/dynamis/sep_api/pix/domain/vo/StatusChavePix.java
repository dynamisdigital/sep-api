package com.dynamis.sep_api.pix.domain.vo;

/**
 * Ciclo de vida da chave Pix da conta operacional (Sprint 31): {@code ATIVA -> INATIVA} e a unica
 * transicao. Remocao inativa (soft) — o historico nunca e apagado.
 */
public enum StatusChavePix {
    ATIVA,
    INATIVA
}

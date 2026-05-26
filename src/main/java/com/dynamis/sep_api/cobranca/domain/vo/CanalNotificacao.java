package com.dynamis.sep_api.cobranca.domain.vo;

/**
 * Canais de envio de notificacao transacional (Sprint 13 - ADR 0014).
 *
 * <p>Adapters concretos vivem em {@code cobranca.infrastructure.adapter.notification} (Task 13.3).
 * Novos canais (push, WhatsApp) ficam fora do escopo desta sprint.
 */
public enum CanalNotificacao {
    EMAIL,
    SMS
}

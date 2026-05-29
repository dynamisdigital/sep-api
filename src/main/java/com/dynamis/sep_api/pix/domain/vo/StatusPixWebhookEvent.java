package com.dynamis.sep_api.pix.domain.vo;

/**
 * Estados de processamento de um {@code PixWebhookEvent} (Epic 15 / Sprint 19).
 *
 * <p>{@code RECEBIDO} apos registro idempotente; {@code PROCESSADO} quando o evento atualiza o
 * dominio Pix; {@code IGNORADO} para evento desconhecido com motivo tecnico; {@code FALHOU} quando
 * o processamento lanca erro e o evento fica disponivel para reprocesso futuro.
 */
public enum StatusPixWebhookEvent {
    RECEBIDO,
    PROCESSADO,
    IGNORADO,
    FALHOU
}

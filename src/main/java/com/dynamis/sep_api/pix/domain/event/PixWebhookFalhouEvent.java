package com.dynamis.sep_api.pix.domain.event;

/**
 * Disparado quando o processamento de um webhook Pix falha (evento {@code FALHOU} para reprocesso).
 * Consumido pelo {@code PixWebhookAuditListener} para gravar {@code PIX_WEBHOOK_FALHOU}.
 */
public record PixWebhookFalhouEvent(String eventId, String motivo) {}

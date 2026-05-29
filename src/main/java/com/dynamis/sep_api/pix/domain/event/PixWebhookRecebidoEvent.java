package com.dynamis.sep_api.pix.domain.event;

import com.dynamis.sep_api.pix.domain.vo.TipoPixWebhookEvent;

/**
 * Disparado quando um webhook Pix e registrado no outbox ({@code PixWebhookEvent} RECEBIDO).
 * Consumido pelo {@code PixWebhookAuditListener} para gravar {@code PIX_WEBHOOK_RECEBIDO}.
 */
public record PixWebhookRecebidoEvent(String eventId, TipoPixWebhookEvent tipo) {}

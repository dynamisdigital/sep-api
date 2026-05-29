package com.dynamis.sep_api.pix.domain.event;

import com.dynamis.sep_api.pix.domain.vo.TipoPixWebhookEvent;

/**
 * Disparado quando um webhook Pix e processado com sucesso. Consumido pelo
 * {@code PixWebhookAuditListener} para gravar {@code PIX_WEBHOOK_PROCESSADO}.
 */
public record PixWebhookProcessadoEvent(String eventId, TipoPixWebhookEvent tipo) {}

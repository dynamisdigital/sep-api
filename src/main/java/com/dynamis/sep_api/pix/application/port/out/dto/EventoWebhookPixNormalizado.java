package com.dynamis.sep_api.pix.application.port.out.dto;

import com.dynamis.sep_api.pix.domain.vo.TipoPixWebhookEvent;

import java.math.BigDecimal;

/**
 * Evento de webhook Pix ja normalizado pelo adapter: traduz o payload cru Celcoin para a linguagem
 * de dominio SEP. O {@code payloadHash} (SHA-256 hex) e calculado pelo adapter a partir do corpo
 * bruto — o use case de processamento nunca ve o JSON original (minimizacao de dados sensiveis).
 *
 * <p>Campos opcionais ({@code endToEndId}, {@code valor}, {@code externalId}) podem ser nulos
 * conforme o {@code tipo} do evento.
 */
public record EventoWebhookPixNormalizado(
        TipoPixWebhookEvent tipo,
        String eventId,
        String endToEndId,
        BigDecimal valor,
        String externalId,
        String payloadHash) {}

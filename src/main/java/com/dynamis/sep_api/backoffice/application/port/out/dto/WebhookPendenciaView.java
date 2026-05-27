package com.dynamis.sep_api.backoffice.application.port.out.dto;

import java.util.UUID;

/**
 * Projecao minima de webhook nao processado (Sprint 14 Task 14.2 — fix review manual). O flag
 * {@code falhou} indica se o status era {@code FALHOU} (true) ou {@code PENDENTE} (false), pra que
 * o listener module prioridade do item da fila sem importar o enum interno do modulo {@code shared}.
 */
public record WebhookPendenciaView(UUID webhookEventId, String provider, boolean falhou) {}

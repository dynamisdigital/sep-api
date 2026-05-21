package com.dynamis.sep_api.contratos.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Disparado quando callback do provider reporta abertura do link de assinatura pelo signatario
 * (Sprint 11 Task 11.8). Evento informativo — nao transiciona contrato.
 */
public record AssinaturaVisualizadaEvent(
        UUID contratoId, UUID tomadorId, UUID envelopeId, String provider, OffsetDateTime dataEvento) {}

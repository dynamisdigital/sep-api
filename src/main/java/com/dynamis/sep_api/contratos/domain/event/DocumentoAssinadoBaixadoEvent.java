package com.dynamis.sep_api.contratos.domain.event;

import java.util.UUID;

/**
 * Disparado a cada download do PDF assinado (Sprint 11 Task 11.8). Audit listener grava
 * {@code DOCUMENTO_ASSINADO_BAIXADO} com ip+user-agent (LGPD) e identificadores tecnicos.
 *
 * <p>{@code baixadoPorId} pode ser o tomador (ownership) ou financeiro/admin (operador interno).
 */
public record DocumentoAssinadoBaixadoEvent(
        UUID contratoId,
        UUID envelopeId,
        UUID documentoAssinadoId,
        UUID baixadoPorId,
        String ipOrigem,
        String userAgentOrigem) {}

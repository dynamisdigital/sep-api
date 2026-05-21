package com.dynamis.sep_api.contratos.domain.event;

import java.util.UUID;

/**
 * Disparado apos provider de assinatura confirmar recebimento do envelope (Sprint 11 Task 11.8).
 * Audit listener grava {@code ASSINATURA_ENVIADA} com identificadores tecnicos + hash; sem PDF.
 */
public record AssinaturaEnviadaEvent(
        UUID contratoId,
        UUID propostaId,
        UUID tomadorId,
        UUID versaoId,
        UUID envelopeId,
        String idEnvelopeExterno,
        String provider,
        String hashPdfEnviado) {}

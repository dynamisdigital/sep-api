package com.dynamis.sep_api.cobranca.domain.event;

import java.util.UUID;

/**
 * Disparado quando o financeiro propoe uma renegociacao (Sprint 13 Task 13.6). Consumido pela
 * auditoria reforcada e pelo listener que notifica o tomador (Task 13.6).
 */
public record RenegociacaoPropostaEvent(
        UUID renegociacaoId, UUID parcelaOriginalId, UUID tomadorId, UUID propostaPor) {}

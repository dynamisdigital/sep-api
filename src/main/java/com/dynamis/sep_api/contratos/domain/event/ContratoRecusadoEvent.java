package com.dynamis.sep_api.contratos.domain.event;

import java.util.UUID;

/** Disparado quando signatario recusa assinatura (Sprint 11 Task 11.5). */
public record ContratoRecusadoEvent(UUID contratoId, UUID propostaId, UUID tomadorId, UUID versaoId, UUID envelopeId) {}

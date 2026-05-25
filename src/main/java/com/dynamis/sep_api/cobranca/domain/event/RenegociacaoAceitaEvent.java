package com.dynamis.sep_api.cobranca.domain.event;

import java.util.UUID;

/**
 * Disparado apos o tomador aceitar uma renegociacao (Sprint 13 Task 13.6). A {@code
 * AgendaPagamento} substituta ja foi persistida quando o evento eh emitido.
 */
public record RenegociacaoAceitaEvent(
        UUID renegociacaoId, UUID parcelaOriginalId, UUID agendaOriginalId, UUID agendaSubstitutaId, UUID tomadorId) {}

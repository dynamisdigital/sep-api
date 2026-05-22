package com.dynamis.sep_api.cobranca.domain.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Disparado quando o job diario marca uma {@code ParcelaCobranca} como {@code ATRASADA} (Sprint
 * 12 Task 12.5). Consumido pela Sprint 13 (inadimplencia) pra disparar cobranca ativa.
 */
public record ParcelaAtrasouEvent(
        UUID parcelaId, UUID agendaId, UUID contratoId, int numero, LocalDate dataVencimento) {}

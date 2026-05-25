package com.dynamis.sep_api.cobranca.domain.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Disparado quando o job de inadimplencia transiciona {@code ParcelaCobranca} {@code ATRASADA -&gt;
 * INADIMPLENTE} (Sprint 13 Task 13.5). Consumido por auditoria e fila operacional (Sprint 14).
 */
public record ParcelaInadimplenteEvent(
        UUID parcelaId,
        UUID agendaId,
        UUID contratoId,
        UUID tomadorId,
        int numero,
        LocalDate dataVencimento,
        int diasAtraso) {}

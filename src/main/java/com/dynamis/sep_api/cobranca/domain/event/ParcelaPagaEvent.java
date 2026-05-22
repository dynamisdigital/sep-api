package com.dynamis.sep_api.cobranca.domain.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Disparado quando uma {@code ParcelaCobranca} transiciona pra {@code PAGA} (Sprint 12 Task
 * 12.4). Publicado apenas uma vez por parcela; subsequentes pagamentos idempotentes nao re-emitem
 * o evento.
 */
public record ParcelaPagaEvent(UUID parcelaId, UUID agendaId, UUID contratoId, int numero, BigDecimal totalRecebido) {}

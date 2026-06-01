package com.dynamis.sep_api.pix.application.port.out.dto;

import java.util.UUID;

/**
 * Projecao read-only da agenda de cobranca ativa de um contrato (Sprint 20 Task 20.1). A existencia
 * de agenda ativa eh pre-condicao de desembolso: sem agenda gerada nao ha plano de pagamento que
 * justifique liberar recursos ao tomador.
 */
public record AgendaDesembolsoView(UUID contratoId, UUID agendaId, int numeroParcelas) {}

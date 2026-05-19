package com.dynamis.sep_api.credito.domain.event;

import java.util.UUID;

/**
 * Evento publicado quando consentimento Open Finance e autorizado pelo tomador (Sprint 9).
 * Consumido pelo audit listener (Task 9.7) pra gravar {@code OPEN_FINANCE_AUTORIZADO} e tambem
 * pelo listener que dispara consulta de movimentacao.
 */
public record OpenFinanceAutorizadoEvent(
        UUID consentimentoId, UUID propostaId, UUID tomadorId, String idExternoCelcoin) {}

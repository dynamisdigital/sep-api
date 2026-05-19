package com.dynamis.sep_api.credito.domain.event;

import java.util.UUID;

/**
 * Evento publicado quando consentimento Open Finance e iniciado para uma proposta (Sprint 9).
 * Consumido pelo audit listener (Task 9.7) pra gravar {@code OPEN_FINANCE_CONSENTIMENTO_INICIADO}.
 */
public record OpenFinanceConsentimentoIniciadoEvent(
        UUID consentimentoId, UUID propostaId, UUID tomadorId, String idExternoCelcoin) {}

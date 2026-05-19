package com.dynamis.sep_api.credito.application.dto;

import java.util.UUID;

/**
 * Command para iniciar consentimento Open Finance numa proposta (Sprint 9 Task 9.3). Validacoes de
 * ownership e estado da proposta acontecem no use case.
 */
public record IniciarConsentimentoOpenFinanceCommand(
        UUID propostaId, UUID tomadorId, String cpfCnpjTomador, String redirectUri) {}

package com.dynamis.sep_api.onboarding.domain.event;

import java.util.UUID;

/** Evento publicado quando uma solicitacao de onboarding KYC PF e iniciada. */
public record OnboardingIniciadoEvent(UUID solicitacaoId, UUID usuarioId) {}

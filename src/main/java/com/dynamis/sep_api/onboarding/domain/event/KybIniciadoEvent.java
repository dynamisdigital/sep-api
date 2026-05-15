package com.dynamis.sep_api.onboarding.domain.event;

import java.util.UUID;

/** Evento publicado quando uma solicitacao de onboarding KYB PJ e iniciada (Sprint 7). */
public record KybIniciadoEvent(UUID solicitacaoId, UUID usuarioId, String cnpj) {}

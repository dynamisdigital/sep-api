package com.dynamis.sep_api.onboarding.domain.event;

import java.util.UUID;

/** Evento publicado quando a verificacao KYC e disparada no provider externo. */
public record VerificacaoKycDisparadaEvent(UUID solicitacaoId, UUID usuarioId, String idVerificacaoExterna) {}

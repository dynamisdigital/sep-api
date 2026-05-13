package com.dynamis.sep_api.onboarding.domain.event;

import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;

import java.util.UUID;

/** Evento publicado quando o webhook Celcoin finaliza a verificacao KYC com status final. */
public record OnboardingFinalizadoEvent(
        UUID solicitacaoId, UUID usuarioId, StatusOnboarding statusFinal, String idVerificacaoExterna) {}

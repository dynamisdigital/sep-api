package com.dynamis.sep_api.onboarding.application.query;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura (read query) publicada pelo modulo {@code onboarding} pra consumo pelo modulo
 * {@code credito} (Sprint 8 Task 8.3). Substitui acesso direto a {@code
 * SolicitacaoOnboardingRepository} de fora do modulo dono.
 */
public interface ConsultarOnboardingParaCreditoQuery {

    /** Consulta resumida da solicitacao por id. Vazia se nao existir. */
    Optional<OnboardingResumoCredito> consultarPorId(UUID solicitacaoId);
}

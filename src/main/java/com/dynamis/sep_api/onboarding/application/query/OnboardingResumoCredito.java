package com.dynamis.sep_api.onboarding.application.query;

import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Resumo read-only de uma {@code SolicitacaoOnboarding} consumido pelo modulo {@code credito}
 * (Sprint 8). Mantem o modulo {@code credito} desacoplado dos repositorios internos de {@code
 * onboarding} (regra DDD do projeto — ver AGENT.md).
 *
 * <p>{@code dataNascimento} preenchido apenas pra PF; {@code dataAbertura} preenchido apenas pra
 * PJ quando a {@code ConsultaCNPJ} tiver retornado essa informacao do provider (Sprint 7).
 */
public record OnboardingResumoCredito(
        UUID solicitacaoId,
        UUID usuarioId,
        TipoSolicitante tipoSolicitante,
        StatusOnboarding status,
        LocalDate dataNascimento,
        LocalDate dataAbertura) {}

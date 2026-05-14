package com.dynamis.sep_api.onboarding.domain.vo;

/**
 * Discriminador do agregado {@link com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding}.
 *
 * <p>{@code PESSOA} dispara KYC PF (Sprint 6); {@code EMPRESA} dispara KYB PJ (Sprint 7).
 */
public enum TipoSolicitante {
    PESSOA,
    EMPRESA
}

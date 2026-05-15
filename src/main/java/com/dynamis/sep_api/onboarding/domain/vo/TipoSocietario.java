package com.dynamis.sep_api.onboarding.domain.vo;

/**
 * Tipo societario da PJ no registro KYB. Mapeado para {@code VARCHAR} via
 * {@code EnumType.STRING} — qualquer adicao precisa de migration ampliando o {@code CHECK}.
 */
public enum TipoSocietario {
    LTDA,
    SA,
    EIRELI,
    MEI,
    OUTROS
}

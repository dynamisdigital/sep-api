package com.dynamis.sep_api.onboarding.domain.vo;

/**
 * Tipos de documento aceitos em uma solicitacao de onboarding KYC PF.
 *
 * <p>Persistido como string (check constraint SQL) para manter compatibilidade com migrations e
 * permitir uso em consultas JPQL.
 */
public enum TipoDocumento {
    RG,
    CNH,
    PASSAPORTE,
    SELFIE
}

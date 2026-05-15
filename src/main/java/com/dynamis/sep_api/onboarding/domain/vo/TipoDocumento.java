package com.dynamis.sep_api.onboarding.domain.vo;

/**
 * Tipos de documento aceitos em uma solicitacao de onboarding.
 *
 * <p>PF (Sprint 6): {@code RG}, {@code CNH}, {@code PASSAPORTE}, {@code SELFIE}.
 *
 * <p>PJ (Sprint 7): {@code CONTRATO_SOCIAL} (LTDA/SA/EIRELI), {@code CCMEI} (MEI),
 * {@code COMPROVANTE_ENDERECO}.
 *
 * <p>Persistido como string (check constraint SQL) para manter compatibilidade com migrations e
 * permitir uso em consultas JPQL.
 */
public enum TipoDocumento {
    RG,
    CNH,
    PASSAPORTE,
    SELFIE,
    CONTRATO_SOCIAL,
    CCMEI,
    COMPROVANTE_ENDERECO
}

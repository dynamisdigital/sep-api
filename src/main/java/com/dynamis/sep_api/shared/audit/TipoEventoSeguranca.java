package com.dynamis.sep_api.shared.audit;

/**
 * Tipos de eventos persistidos em {@code audit_log_seguranca} (Sprint 5 Task 5.7).
 *
 * <p>Mantido como enum simples (nao sealed) para casar com o check constraint da migration V4 e
 * permitir uso em consultas JPQL.
 *
 * <p>Sprint 6 Task 6.1: adicionados 6 eventos {@code KYC_*} para a trilha auditavel reforcada do
 * modulo {@code onboarding}. Check constraint atualizado em V7.
 */
public enum TipoEventoSeguranca {
    LOGIN_OK,
    LOGIN_FAIL,
    TOTP_OK,
    TOTP_FAIL,
    BACKUP_CODE_USED,
    LOCKOUT,
    PASSWORD_CHANGED,
    MFA_ENABLED,
    MFA_DISABLED,
    REFRESH_REUSE_DETECTED,
    STEP_UP_OK,
    STEP_UP_FAIL,
    KYC_INICIADO,
    KYC_DOCUMENTO_ENVIADO,
    KYC_VERIFICACAO_DISPARADA,
    KYC_FINALIZADO_APROVADO,
    KYC_FINALIZADO_REPROVADO,
    KYC_FINALIZADO_PENDENCIA
}

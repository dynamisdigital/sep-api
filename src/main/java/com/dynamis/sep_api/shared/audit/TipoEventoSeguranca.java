package com.dynamis.sep_api.shared.audit;

/**
 * Tipos de eventos persistidos em {@code audit_log_seguranca} (Sprint 5 Task 5.7).
 *
 * <p>Mantido como enum simples (nao sealed) para casar com o check constraint da migration V4 e
 * permitir uso em consultas JPQL.
 *
 * <p>Sprint 6 Task 6.1: adicionados 6 eventos {@code KYC_*} para a trilha auditavel reforcada do
 * modulo {@code onboarding}. Check constraint atualizado em V7.
 *
 * <p>Sprint 7 Task 7.8: adicionados 7 eventos {@code KYB_*} e {@code PLD_*} para a trilha
 * regulatoria PJ + Prevencao a Lavagem de Dinheiro (Resolucao CMN 4.656/2018). Check constraint
 * atualizado em V13.
 *
 * <p>Sprint 8 Task 8.4: adicionado {@code ROLE_ALTERADO} para auditar promocao/rebaixamento de
 * roles por ADMIN. Check constraint atualizado em V15.
 *
 * <p>Sprint 8 Task 8.6: adicionados 5 eventos {@code PROPOSTA_*}/{@code PARECER_*} para a trilha
 * auditavel reforcada da analise de credito (CMN 4.656/2018 Art. 9). Check constraint atualizado
 * em V16.
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
    KYC_FINALIZADO_PENDENCIA,
    KYB_INICIADO,
    KYB_FINALIZADO_APROVADO,
    KYB_FINALIZADO_REPROVADO,
    PLD_INICIADO,
    PLD_HIT_DETECTADO,
    PLD_LIMPO,
    PLD_FINALIZADO,
    ROLE_ALTERADO,
    PROPOSTA_CRIADA,
    PROPOSTA_AVALIADA_MOTOR,
    PARECER_REGISTRADO,
    PROPOSTA_APROVADA,
    PROPOSTA_REJEITADA
}

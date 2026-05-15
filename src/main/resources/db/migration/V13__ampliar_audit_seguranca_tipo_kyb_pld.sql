-- =============================================================================
-- V13 — Sprint 7 Task 7.8: amplia chk_audit_seguranca_tipo com eventos KYB e PLD
-- =============================================================================
-- Adiciona os 7 novos tipos da trilha auditavel reforcada PJ + PLD:
--   KYB_INICIADO, KYB_FINALIZADO_APROVADO, KYB_FINALIZADO_REPROVADO,
--   PLD_INICIADO, PLD_HIT_DETECTADO, PLD_LIMPO, PLD_FINALIZADO.
-- Mantem todos os tipos anteriores (Sprint 5 seguranca + Sprint 6 KYC).
-- Nao recria a tabela audit_log_seguranca.
-- =============================================================================

ALTER TABLE audit_log_seguranca DROP CONSTRAINT chk_audit_seguranca_tipo;

ALTER TABLE audit_log_seguranca ADD CONSTRAINT chk_audit_seguranca_tipo CHECK (tipo IN (
    'LOGIN_OK', 'LOGIN_FAIL',
    'TOTP_OK', 'TOTP_FAIL',
    'BACKUP_CODE_USED',
    'LOCKOUT',
    'PASSWORD_CHANGED',
    'MFA_ENABLED', 'MFA_DISABLED',
    'REFRESH_REUSE_DETECTED',
    'STEP_UP_OK', 'STEP_UP_FAIL',
    'KYC_INICIADO',
    'KYC_DOCUMENTO_ENVIADO',
    'KYC_VERIFICACAO_DISPARADA',
    'KYC_FINALIZADO_APROVADO',
    'KYC_FINALIZADO_REPROVADO',
    'KYC_FINALIZADO_PENDENCIA',
    'KYB_INICIADO',
    'KYB_FINALIZADO_APROVADO',
    'KYB_FINALIZADO_REPROVADO',
    'PLD_INICIADO',
    'PLD_HIT_DETECTADO',
    'PLD_LIMPO',
    'PLD_FINALIZADO'
));

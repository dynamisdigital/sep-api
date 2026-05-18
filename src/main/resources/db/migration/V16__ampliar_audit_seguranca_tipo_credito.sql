-- =============================================================================
-- V16 — Sprint 8 Task 8.6: amplia chk_audit_seguranca_tipo com eventos de credito
-- =============================================================================
-- Adiciona os 5 tipos novos da trilha auditavel reforcada da analise de credito
-- (CMN 4.656/2018 Art. 9):
--   PROPOSTA_CRIADA, PROPOSTA_AVALIADA_MOTOR, PARECER_REGISTRADO,
--   PROPOSTA_APROVADA, PROPOSTA_REJEITADA.
--
-- Mantem todos os tipos anteriores (Sprint 5 seguranca + Sprint 6 KYC +
-- Sprint 7 KYB/PLD + Sprint 8 Task 8.4 ROLE_ALTERADO).
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
    'PLD_FINALIZADO',
    'ROLE_ALTERADO',
    'PROPOSTA_CRIADA',
    'PROPOSTA_AVALIADA_MOTOR',
    'PARECER_REGISTRADO',
    'PROPOSTA_APROVADA',
    'PROPOSTA_REJEITADA'
));

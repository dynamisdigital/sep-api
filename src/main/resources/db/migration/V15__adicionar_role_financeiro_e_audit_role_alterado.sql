-- =============================================================================
-- V15 — Sprint 8 Task 8.4: nova role FINANCEIRO + audit ROLE_ALTERADO
-- =============================================================================
-- Adiciona a role 'FINANCEIRO' (operador interno do time financeiro) ao check
-- constraint de usuario.role. Preserva dados existentes (so altera o conjunto
-- de valores aceitos).
--
-- Adiciona tambem o evento ROLE_ALTERADO em chk_audit_seguranca_tipo para
-- registrar promocao/rebaixamento de role por ADMIN.
--
-- Eventos PROPOSTA_* / PARECER_* virao em V16 (Task 8.6).
-- =============================================================================

-- 1. Ampliar role do usuario
ALTER TABLE usuario DROP CONSTRAINT chk_usuario_role;

ALTER TABLE usuario ADD CONSTRAINT chk_usuario_role CHECK (role IN ('ADMIN', 'CLIENTE', 'FINANCEIRO'));

COMMENT ON COLUMN usuario.role IS 'Perfil do usuario: ADMIN (interno full), CLIENTE (tomador/credor), FINANCEIRO (parecer de credito Sprint 8).';

-- 2. Ampliar chk_audit_seguranca_tipo com ROLE_ALTERADO
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
    'ROLE_ALTERADO'
));

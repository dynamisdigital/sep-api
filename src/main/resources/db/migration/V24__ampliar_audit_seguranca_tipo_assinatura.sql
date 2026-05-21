-- =============================================================================
-- V24 — Sprint 11 Task 11.8: amplia chk_audit_seguranca_tipo com eventos de
-- assinatura digital + CCB.
-- =============================================================================
-- Adiciona 6 tipos novos do ciclo de formalizacao contratual (Epic 7 parte 2):
--   CCB_GERADA                  — PDF da CCB gerado pelo CcbGenerator
--   ASSINATURA_ENVIADA          — envelope criado no provider (Clicksign)
--   ASSINATURA_VISUALIZADA      — tomador abriu link de assinatura
--   ASSINATURA_ASSINADA         — callback ASSINADO; PDF assinado armazenado
--   ASSINATURA_RECUSADA         — callback RECUSADO
--   DOCUMENTO_ASSINADO_BAIXADO  — download do PDF assinado (grava ip+user-agent)
--
-- Exigencia regulatoria: CMN 4.656/2018 + Lei 10.931/2004 (CCB titulo executivo
-- extrajudicial) + LGPD. Retencao minima 10 anos (mesma das entidades de
-- contratos/versoes/aceites).
--
-- Mantem todos os tipos anteriores. Forward-only.
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
    'PROPOSTA_REJEITADA',
    'OPEN_FINANCE_CONSENTIMENTO_INICIADO',
    'OPEN_FINANCE_AUTORIZADO',
    'OPEN_FINANCE_NEGADO',
    'OPEN_FINANCE_DADOS_RECEBIDOS',
    'OPEN_FINANCE_REAVALIACAO',
    'CONTRATO_GERADO',
    'CONTRATO_NOVA_VERSAO',
    'CONTRATO_ACEITO',
    'CONTRATO_CANCELADO',
    'CCB_GERADA',
    'ASSINATURA_ENVIADA',
    'ASSINATURA_VISUALIZADA',
    'ASSINATURA_ASSINADA',
    'ASSINATURA_RECUSADA',
    'DOCUMENTO_ASSINADO_BAIXADO'
));

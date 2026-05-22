-- =============================================================================
-- V29 — Sprint 12 Task 12.7: amplia chk_audit_seguranca_tipo com eventos de
-- cobranca (Epic 8 parte 1).
-- =============================================================================
-- Adiciona 6 tipos novos do ciclo de cobranca:
--   AGENDA_GERADA                — agenda criada apos contrato assinado
--   PARCELA_CRIADA               — uma por parcela da agenda nascente
--   RECEBIMENTO_REGISTRADO       — operador registra pagamento manual
--   PARCELA_PAGA                 — parcela transiciona pra PAGA
--   PARCELA_ATRASADA             — job marca parcela vencida
--   MOVIMENTACAO_ESCROW_CRIADA   — escrow recebe ENTRADA segregada
--
-- Exigencia regulatoria: CMN 4.656/2018 Art. 9 (analise de credito) + Art. 11
-- (contratos) + segregacao patrimonial. Retencao minima alinhada com
-- contratos/versoes (10 anos).
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
    'DOCUMENTO_ASSINADO_BAIXADO',
    'AGENDA_GERADA',
    'PARCELA_CRIADA',
    'RECEBIMENTO_REGISTRADO',
    'PARCELA_PAGA',
    'PARCELA_ATRASADA',
    'MOVIMENTACAO_ESCROW_CRIADA'
));

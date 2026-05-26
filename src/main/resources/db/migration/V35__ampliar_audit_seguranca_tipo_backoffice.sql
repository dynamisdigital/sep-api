-- =============================================================================
-- V35 — Sprint 14 Task 14.8: amplia chk_audit_seguranca_tipo com eventos do
-- modulo backoffice (Epic 9).
-- =============================================================================
-- Adiciona 6 tipos novos:
--   ITEM_FILA_CRIADO           — listener/job cria item na fila operacional
--   ITEM_ASSUMIDO              — operador assume item (transicao ABERTO -> EM_TRATAMENTO)
--   COMENTARIO_REGISTRADO      — comentario interno registrado
--   ITEM_RESOLVIDO             — operador marca resolvido (com justificativa)
--   ITEM_IGNORADO              — operador marca ignorado (com justificativa)
--   REPROCESSO_DISPARADO       — webhook ou provider re-disparado manualmente
--
-- Exigencia regulatoria: CMN 4.656/2018 + LGPD — trilha auditavel reforcada de
-- qualquer acao de operador interno sobre a fila operacional. Justificativas
-- de resolucao/ignorar sao truncadas em 80 caracteres pra audit (conteudo
-- completo fica em comentario_interno). Sem CPF/CNPJ no payload.
--
-- Forward-only. Mantem todos os tipos anteriores.
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
    'MOVIMENTACAO_ESCROW_CRIADA',
    'NOTIFICACAO_ENVIADA',
    'EVENTO_COBRANCA_REGISTRADO',
    'PARCELA_INADIMPLENTE',
    'RENEGOCIACAO_PROPOSTA',
    'RENEGOCIACAO_ACEITA',
    'RENEGOCIACAO_RECUSADA',
    'RENEGOCIACAO_EXPIRADA',
    'ITEM_FILA_CRIADO',
    'ITEM_ASSUMIDO',
    'COMENTARIO_REGISTRADO',
    'ITEM_RESOLVIDO',
    'ITEM_IGNORADO',
    'REPROCESSO_DISPARADO'
));

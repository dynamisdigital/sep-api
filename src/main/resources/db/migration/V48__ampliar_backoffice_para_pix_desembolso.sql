-- =============================================================================
-- V48 — Backoffice acomoda falha de desembolso Pix (Sprint 20 Task 20.4)
-- =============================================================================
-- A falha de uma transferencia Pix de desembolso passa a gerar item na fila
-- operacional e a ser elegivel para reprocesso (re-consulta de status, nunca
-- reenvio — a chave Pix nao eh persistida). Estende os CHECKs de V33:
--   - item_fila_operacional.tipo            += DESEMBOLSO_PIX_FALHOU
--   - item_fila_operacional.tipo_entidade   += PIX_TRANSFERENCIA
--   - reprocesso.tipo_chamada               += PIX_TRANSFERENCIA
-- =============================================================================

ALTER TABLE item_fila_operacional DROP CONSTRAINT chk_item_fila_tipo;
ALTER TABLE item_fila_operacional
    ADD CONSTRAINT chk_item_fila_tipo CHECK (tipo IN (
        'ONBOARDING_PENDENTE',
        'ONBOARDING_ERRO',
        'PROPOSTA_PENDENTE',
        'CONTRATO_NAO_ASSINADO',
        'COBRANCA_INADIMPLENTE',
        'WEBHOOK_FALHOU',
        'DESEMBOLSO_PIX_FALHOU',
        'OUTRO'
    ));

ALTER TABLE item_fila_operacional DROP CONSTRAINT chk_item_fila_tipo_entidade;
ALTER TABLE item_fila_operacional
    ADD CONSTRAINT chk_item_fila_tipo_entidade CHECK (tipo_entidade IN (
        'ONBOARDING',
        'PROPOSTA',
        'CONTRATO',
        'PARCELA_COBRANCA',
        'WEBHOOK_EVENT_LOG',
        'PIX_TRANSFERENCIA',
        'OUTRO'
    ));

ALTER TABLE reprocesso DROP CONSTRAINT chk_reprocesso_tipo_chamada;
ALTER TABLE reprocesso
    ADD CONSTRAINT chk_reprocesso_tipo_chamada CHECK (
        tipo_chamada IS NULL OR tipo_chamada IN (
            'KYC', 'KYB', 'PLD', 'OPEN_FINANCE', 'ASSINATURA_DIGITAL', 'PIX_TRANSFERENCIA'
        )
    );

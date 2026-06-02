-- =============================================================================
-- V52 — Backoffice acomoda divergencia de recebimento Pix (Sprint 21 Task 21.5)
-- =============================================================================
-- Uma divergencia de recebimento Pix (referencia desconhecida/nao-ATIVA, endToEndId
-- ausente, valor parcial/maior ou falha de baixa) passa a gerar item na fila
-- operacional. Estende os CHECKs de V33/V48:
--   - item_fila_operacional.tipo          += RECEBIMENTO_PIX_DIVERGENTE
--   - item_fila_operacional.tipo_entidade += PIX_RECEBIMENTO
-- reprocesso.tipo_chamada NAO muda: o tratamento do recebimento divergente eh
-- operacao assistida (assumir/comentar/resolver/ignorar) — nao ha reprocesso de
-- provider (o Pix ja foi recebido; reenviar nao se aplica).
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
        'RECEBIMENTO_PIX_DIVERGENTE',
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
        'PIX_RECEBIMENTO',
        'OUTRO'
    ));

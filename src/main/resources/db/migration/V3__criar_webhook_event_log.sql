-- =============================================================================
-- Migration V3 - Sprint 4 - Webhook Receiver Pattern
-- =============================================================================
-- Tabela compartilhada (modulo `shared`) que registra todo evento recebido nos
-- endpoints `/api/v1/webhooks/{provider}/{event}`. Usada como Outbox stub para
-- processamento assincrono em Epics futuras (Pix, KYC callbacks, etc.).
-- =============================================================================

CREATE TABLE webhook_event_log (
    id UUID PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    event VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    signature VARCHAR(512),
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL,
    erro TEXT,
    data_recebimento TIMESTAMP WITH TIME ZONE NOT NULL,
    data_processamento TIMESTAMP WITH TIME ZONE,

    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL DEFAULT 'system',
    modificado_por VARCHAR(50) NOT NULL DEFAULT 'system',

    CONSTRAINT uq_webhook_event_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_webhook_event_status CHECK (status IN ('PENDENTE', 'PROCESSADO', 'FALHOU'))
);

CREATE INDEX idx_webhook_event_provider_event ON webhook_event_log (provider, event);
CREATE INDEX idx_webhook_event_status_recebimento ON webhook_event_log (status, data_recebimento DESC);

COMMENT ON TABLE webhook_event_log IS 'Outbox stub de webhooks recebidos (Sprint 4). Apenas registra como PENDENTE; processamento futuro nas Epics 5/15.';
COMMENT ON COLUMN webhook_event_log.idempotency_key IS 'Chave unica fornecida pelo provider externo (header Idempotency-Key).';
COMMENT ON COLUMN webhook_event_log.signature IS 'Assinatura HMAC-SHA256 hex (header X-Webhook-Signature).';

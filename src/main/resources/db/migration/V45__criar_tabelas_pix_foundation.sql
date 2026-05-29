-- =============================================================================
-- V45 — Foundation do modulo Pix (Sprint 19 — Epic 15 parte 1)
-- =============================================================================
-- Cria a base persistente do modulo pix: transferencias (saida), recebimentos
-- (entrada) e snapshot de eventos de webhook. Foundation: nao executa desembolso
-- real nem concilia parcela de cobranca (Sprints 20/21). Integracao Celcoin fica
-- isolada por Provider Pattern; estas tabelas guardam apenas estado de dominio.
--
-- Tabelas:
--   - pix_transferencia  (intencao de saida; idempotency_key UNIQUE global)
--   - pix_recebimento    (entrada identificada; end_to_end_id UNIQUE parcial)
--   - pix_webhook_event  (snapshot do evento; UNIQUE (provider, event_id))
--
-- Decisoes:
--   - Sem FK para contrato/operacao/carteira nesta sprint (foundation).
--   - Nenhuma FK com ON DELETE CASCADE (trilha auditavel — CMN 4.656/2018 + LGPD).
--   - pix_webhook_event NAO persiste payload bruto: apenas payload_hash (SHA-256
--     hex, 64 chars) por minimizacao de dados sensiveis.
--   - Indices por status/data para fila operacional futura.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Transferencia Pix (saida)
-- -----------------------------------------------------------------------------
CREATE TABLE pix_transferencia (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(40) NOT NULL,
    valor NUMERIC(19, 2) NOT NULL,
    descricao VARCHAR(255),
    external_id VARCHAR(100),
    correlation_id VARCHAR(100),
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT chk_pix_transferencia_status CHECK (
        status IN ('CRIADA', 'SOLICITADA', 'PROCESSANDO', 'CONCLUIDA', 'FALHOU', 'CANCELADA')
    ),
    CONSTRAINT chk_pix_transferencia_valor CHECK (valor > 0)
);

CREATE INDEX idx_pix_transferencia_status_data ON pix_transferencia (status, data_criacao);

COMMENT ON TABLE pix_transferencia IS 'Intencao de saida Pix (Sprint 19 foundation). Desembolso real fica para Sprints 20/21.';
COMMENT ON COLUMN pix_transferencia.external_id IS 'Id externo retornado pelo provider apos solicitacao.';

-- -----------------------------------------------------------------------------
-- 2. Recebimento Pix (entrada)
-- -----------------------------------------------------------------------------
CREATE TABLE pix_recebimento (
    id UUID PRIMARY KEY,
    end_to_end_id VARCHAR(100),
    valor NUMERIC(19, 2) NOT NULL,
    status VARCHAR(40) NOT NULL,
    recebido_em TIMESTAMP WITH TIME ZONE NOT NULL,
    correlation_id VARCHAR(100),
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT chk_pix_recebimento_status CHECK (
        status IN ('RECEBIDO', 'EM_PROCESSAMENTO', 'CONCILIADO', 'NAO_IDENTIFICADO', 'FALHOU')
    ),
    CONSTRAINT chk_pix_recebimento_valor CHECK (valor > 0)
);

-- UNIQUE parcial: dedup por id E2E do arranjo Pix apenas quando presente.
CREATE UNIQUE INDEX uq_pix_recebimento_end_to_end_id
    ON pix_recebimento (end_to_end_id)
    WHERE end_to_end_id IS NOT NULL;

CREATE INDEX idx_pix_recebimento_status_data ON pix_recebimento (status, recebido_em);

COMMENT ON TABLE pix_recebimento IS 'Entrada Pix identificada por evento de provider (Sprint 19 foundation). Conciliacao de parcela fica para Sprints 20/21.';

-- -----------------------------------------------------------------------------
-- 3. Snapshot de evento de webhook Pix
-- -----------------------------------------------------------------------------
CREATE TABLE pix_webhook_event (
    id UUID PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    event_id VARCHAR(120) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    erro VARCHAR(255),
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT uq_pix_webhook_event_provider_event UNIQUE (provider, event_id),
    CONSTRAINT chk_pix_webhook_event_type CHECK (
        event_type IN ('RECEBIMENTO_PIX', 'STATUS_TRANSFERENCIA', 'DESCONHECIDO')
    ),
    CONSTRAINT chk_pix_webhook_event_status CHECK (
        status IN ('RECEBIDO', 'PROCESSADO', 'IGNORADO', 'FALHOU')
    ),
    CONSTRAINT chk_pix_webhook_event_payload_hash CHECK (payload_hash ~ '^[a-fA-F0-9]{64}$')
);

CREATE INDEX idx_pix_webhook_event_status_data ON pix_webhook_event (status, received_at);

COMMENT ON TABLE pix_webhook_event IS 'Snapshot idempotente de evento de webhook Pix (Sprint 19). Nao persiste payload bruto — apenas payload_hash (SHA-256).';
COMMENT ON COLUMN pix_webhook_event.payload_hash IS 'SHA-256 hex do payload bruto (64 chars). Minimizacao de dados sensiveis.';

-- =============================================================================
-- V54 — Sprint 29 Task 29.1: aporte da credora (Epic 15)
-- =============================================================================
-- Cria aporte_credora: registro do aporte assistido da credora em operacao
-- financiada da carteira, movimentado no escrow via provider fake. Nenhum
-- dinheiro real e movido nesta fase (ativacao Celcoin/BaaS fica para a Fase 5).
--
-- Decisoes:
--   - UNIQUE (operacao_id, idempotency_key): replay do mesmo POST retorna o
--     mesmo aporte (idempotencia por operacao + chave).
--   - FKs internas para operacao_financiada e empresa_credora SEM ON DELETE
--     CASCADE (trilha auditavel — CMN 4.656/2018 + LGPD).
--   - referencia_escrow e interna (nunca exposta em contrato publico).
--   - motivo_falha_sanitizado guarda somente motivo tratado; erro bruto de
--     provider nao e persistido.
--   - Indices por operacao e por credora para leitura owner-scoped.
-- =============================================================================

CREATE TABLE aporte_credora (
    id UUID PRIMARY KEY,
    operacao_id UUID NOT NULL,
    empresa_credora_id UUID NOT NULL,
    valor NUMERIC(19, 2) NOT NULL,
    moeda VARCHAR(3) NOT NULL,
    status VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    referencia_escrow VARCHAR(100),
    motivo_falha_sanitizado VARCHAR(255),
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT fk_aporte_operacao FOREIGN KEY (operacao_id) REFERENCES operacao_financiada (id),
    CONSTRAINT fk_aporte_credora FOREIGN KEY (empresa_credora_id) REFERENCES empresa_credora (id),
    CONSTRAINT uq_aporte_operacao_idempotency UNIQUE (operacao_id, idempotency_key),
    CONSTRAINT chk_aporte_status CHECK (
        status IN ('PENDENTE', 'EM_PROCESSAMENTO', 'LIQUIDADO', 'FALHOU')
    ),
    CONSTRAINT chk_aporte_valor CHECK (valor > 0)
);

CREATE INDEX idx_aporte_credora_operacao ON aporte_credora (operacao_id, data_criacao);
CREATE INDEX idx_aporte_credora_empresa ON aporte_credora (empresa_credora_id, data_criacao);

COMMENT ON TABLE aporte_credora IS 'Aporte assistido da credora em operacao financiada (Sprint 29). Provider fake — sem dinheiro real; Celcoin/BaaS real na Fase 5.';
COMMENT ON COLUMN aporte_credora.referencia_escrow IS 'Referencia interna da movimentacao no escrow; nunca exposta em contrato publico.';
COMMENT ON COLUMN aporte_credora.motivo_falha_sanitizado IS 'Motivo de falha ja sanitizado; erro bruto de provider nao e persistido.';

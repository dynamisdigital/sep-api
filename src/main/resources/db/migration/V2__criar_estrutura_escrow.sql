-- =============================================================================
-- Migration V2 - Sprint 1 - Estrutura inicial do modulo escrow (CMN 4.656/2018, ADR 0005)
-- =============================================================================
-- Modela conta_escrow, wallet e movimentacao_escrow. Entidades da Sprint 1;
-- regras concretas de movimentacao (Pix, abertura via Celcoin) entram na Epic 15.
-- =============================================================================

CREATE TABLE conta_escrow (
    id UUID PRIMARY KEY,
    external_id VARCHAR(100),
    titular VARCHAR(255) NOT NULL,
    status VARCHAR(40) NOT NULL,

    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL DEFAULT 'system',
    modificado_por VARCHAR(50) NOT NULL DEFAULT 'system',

    CONSTRAINT chk_conta_escrow_status CHECK (status IN ('EM_ABERTURA', 'ATIVA', 'BLOQUEADA', 'ENCERRADA'))
);
CREATE UNIQUE INDEX idx_conta_escrow_external_id ON conta_escrow (external_id) WHERE external_id IS NOT NULL;
COMMENT ON TABLE conta_escrow IS 'Conta escrow segregada (Resolucao CMN 4.656/2018, ADR 0005). Sprint 1: schema; Epic 15: regra de negocio.';

CREATE TABLE wallet (
    id UUID PRIMARY KEY,
    conta_escrow_id UUID NOT NULL REFERENCES conta_escrow (id),
    proposta_id UUID,
    tipo_wallet VARCHAR(40) NOT NULL,
    saldo NUMERIC(19, 2) NOT NULL DEFAULT 0,

    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL DEFAULT 'system',
    modificado_por VARCHAR(50) NOT NULL DEFAULT 'system',

    CONSTRAINT chk_wallet_tipo CHECK (tipo_wallet IN ('PROPOSTA', 'OPERACAO', 'RESERVA_OPERACIONAL')),
    CONSTRAINT chk_wallet_saldo_nao_negativo CHECK (saldo >= 0)
);
CREATE INDEX idx_wallet_conta_escrow ON wallet (conta_escrow_id);
CREATE INDEX idx_wallet_proposta ON wallet (proposta_id) WHERE proposta_id IS NOT NULL;

CREATE TABLE movimentacao_escrow (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallet (id),
    tipo VARCHAR(40) NOT NULL,
    valor NUMERIC(19, 2) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    status VARCHAR(40) NOT NULL,
    data_movimentacao TIMESTAMP WITH TIME ZONE NOT NULL,

    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL DEFAULT 'system',
    modificado_por VARCHAR(50) NOT NULL DEFAULT 'system',

    CONSTRAINT uq_movimentacao_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_movimentacao_tipo CHECK (tipo IN ('Aporte', 'Desembolso', 'Recebimento', 'Retirada')),
    CONSTRAINT chk_movimentacao_status CHECK (status IN ('INICIADA', 'EM_PROCESSAMENTO', 'LIQUIDADA', 'FALHOU', 'REVERTIDA')),
    CONSTRAINT chk_movimentacao_valor_positivo CHECK (valor > 0)
);
CREATE INDEX idx_movimentacao_wallet ON movimentacao_escrow (wallet_id);
CREATE INDEX idx_movimentacao_status_data ON movimentacao_escrow (status, data_movimentacao DESC);
COMMENT ON COLUMN movimentacao_escrow.idempotency_key IS 'Chave unica de idempotencia para evitar movimentacoes duplicadas (Header Idempotency-Key).';

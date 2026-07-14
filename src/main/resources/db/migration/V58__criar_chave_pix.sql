-- =============================================================================
-- V58 — Sprint 31 Task 31.2: chave Pix da conta operacional/escrow (Epic 15)
-- =============================================================================
-- Cria chave_pix: gestao assistida (financeiro/admin) de chaves Pix da conta
-- operacional/escrow via Provider Pattern (fake default; Celcoin skeleton).
-- Nenhum dinheiro e movido nesta sprint.
--
-- Decisoes:
--   - Minimizacao (CMN 4.656/2018 + LGPD): NAO existe coluna de valor bruto;
--     apenas valor_hash (SHA-256 hex, 64) e valor_mascarado (leitura, <= 80).
--   - provider_key_id e idempotency_key sao identificadores tecnicos internos;
--     nunca expostos em contrato publico.
--   - UNIQUE (conta_escrow_id, idempotency_key): replay do mesmo POST retorna
--     o mesmo recurso.
--   - UNIQUE parcial (conta_escrow_id, tipo, valor_hash) WHERE status='ATIVA':
--     uma unica chave ativa por valor normalizado na conta, mesmo sob corrida;
--     historico INATIVA nao bloqueia novo cadastro.
--   - FK para conta_escrow SEM ON DELETE CASCADE (trilha auditavel).
--   - Remocao e logica (ATIVA -> INATIVA); CHECK de coerencia exige ator e
--     instante registrados quando INATIVA e ausentes quando ATIVA.
-- =============================================================================

CREATE TABLE chave_pix (
    id UUID PRIMARY KEY,
    conta_escrow_id UUID NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    valor_hash VARCHAR(64) NOT NULL,
    valor_mascarado VARCHAR(80) NOT NULL,
    status VARCHAR(40) NOT NULL,
    provider_key_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    criada_por_usuario_id UUID NOT NULL,
    removida_por_usuario_id UUID,
    criada_em TIMESTAMP WITH TIME ZONE NOT NULL,
    removida_em TIMESTAMP WITH TIME ZONE,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT fk_chave_pix_conta_escrow FOREIGN KEY (conta_escrow_id) REFERENCES conta_escrow (id),
    CONSTRAINT uq_chave_pix_conta_idempotency UNIQUE (conta_escrow_id, idempotency_key),
    CONSTRAINT chk_chave_pix_status CHECK (status IN ('ATIVA', 'INATIVA')),
    CONSTRAINT chk_chave_pix_tipo CHECK (tipo IN ('CPF', 'CNPJ', 'EMAIL', 'TELEFONE', 'EVP')),
    CONSTRAINT chk_chave_pix_valor_hash CHECK (char_length(valor_hash) = 64),
    CONSTRAINT chk_chave_pix_remocao_coerente CHECK (
        (status = 'ATIVA' AND removida_em IS NULL AND removida_por_usuario_id IS NULL)
        OR (status = 'INATIVA' AND removida_em IS NOT NULL AND removida_por_usuario_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_chave_pix_ativa_por_valor
    ON chave_pix (conta_escrow_id, tipo, valor_hash)
    WHERE status = 'ATIVA';

CREATE INDEX idx_chave_pix_conta_criacao ON chave_pix (conta_escrow_id, criada_em DESC);

COMMENT ON TABLE chave_pix IS 'Chave Pix da conta operacional/escrow, gestao assistida (Sprint 31). Valor bruto nunca persistido: apenas hash SHA-256 + mascara. Provider fake default; Celcoin skeleton na Fase 5.';
COMMENT ON COLUMN chave_pix.valor_hash IS 'SHA-256 hex do valor normalizado; consistencia idempotente sem guardar a chave.';
COMMENT ON COLUMN chave_pix.valor_mascarado IS 'Mascara segura para leitura/auditoria; nunca o valor integral.';
COMMENT ON COLUMN chave_pix.provider_key_id IS 'Identificador tecnico da chave no provider; interno, nunca exposto em contrato publico.';

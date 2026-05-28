-- =============================================================================
-- V40 — Sprint 17 Task 17.2: carteira da credora (Epic 10)
-- =============================================================================
-- Tabelas novas:
--   - oportunidade_investimento  (snapshot 1:1 de proposta elegivel para captacao)
--   - interesse_credora          (manifestacao de interesse credora -> oportunidade)
--   - operacao_financiada        (associacao operacional credora -> contrato; carteira)
--
-- Decisoes:
--   - oportunidade_investimento.proposta_id e UNIQUE: 1 oportunidade por proposta;
--     snapshot materializado por use case de sincronizacao explicito (Task 17.4).
--   - contrato_id e proposta_id sao referencias logicas a modulos credito/contratos
--     (sem FK fisica cross-module, diretriz V33/V38).
--   - FKs internas para empresa_credora e oportunidade_investimento SEM ON DELETE
--     CASCADE (preserva trilha LGPD/CMN 4.656).
--   - UNIQUE parcial uq_interesse_ativo restringe 1 interesse ATIVO por
--     credora+oportunidade; apos cancelar, nova manifestacao e permitida.
--   - Sprint 17 nao move recurso financeiro real (sem aporte/Pix/escrow).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. oportunidade_investimento
-- -----------------------------------------------------------------------------
CREATE TABLE oportunidade_investimento (
    id UUID PRIMARY KEY,
    proposta_id UUID NOT NULL,
    contrato_id UUID,
    valor NUMERIC(19, 2) NOT NULL,
    prazo_meses INTEGER NOT NULL,
    taxa_juros_mensal NUMERIC(9, 6),
    status VARCHAR(20) NOT NULL,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT uq_oportunidade_proposta UNIQUE (proposta_id),
    CONSTRAINT chk_oportunidade_status CHECK (status IN ('DISPONIVEL', 'ENCERRADA')),
    CONSTRAINT chk_oportunidade_valor CHECK (valor >= 0),
    CONSTRAINT chk_oportunidade_prazo CHECK (prazo_meses > 0)
);

CREATE INDEX idx_oportunidade_status ON oportunidade_investimento (status, data_criacao DESC);

COMMENT ON TABLE oportunidade_investimento IS
    'Snapshot operacional de proposta elegivel exposta como oportunidade a credoras (Sprint 17). proposta_id/contrato_id sao referencias logicas.';

-- -----------------------------------------------------------------------------
-- 2. interesse_credora
-- -----------------------------------------------------------------------------
CREATE TABLE interesse_credora (
    id UUID PRIMARY KEY,
    empresa_credora_id UUID NOT NULL,
    oportunidade_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT fk_interesse_credora FOREIGN KEY (empresa_credora_id) REFERENCES empresa_credora (id),
    CONSTRAINT fk_interesse_oportunidade FOREIGN KEY (oportunidade_id) REFERENCES oportunidade_investimento (id),
    CONSTRAINT chk_interesse_status CHECK (status IN ('ATIVO', 'CANCELADO'))
);

CREATE UNIQUE INDEX uq_interesse_ativo
    ON interesse_credora (empresa_credora_id, oportunidade_id)
    WHERE status = 'ATIVO';

CREATE INDEX idx_interesse_credora_status ON interesse_credora (empresa_credora_id, status, data_criacao DESC);

COMMENT ON TABLE interesse_credora IS
    'Manifestacao de interesse de uma credora numa oportunidade (Sprint 17). UNIQUE parcial garante 1 interesse ATIVO por credora+oportunidade.';

-- -----------------------------------------------------------------------------
-- 3. operacao_financiada
-- -----------------------------------------------------------------------------
CREATE TABLE operacao_financiada (
    id UUID PRIMARY KEY,
    empresa_credora_id UUID NOT NULL,
    contrato_id UUID NOT NULL,
    oportunidade_id UUID,
    status VARCHAR(20) NOT NULL,
    justificativa VARCHAR(500) NOT NULL,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT fk_operacao_credora FOREIGN KEY (empresa_credora_id) REFERENCES empresa_credora (id),
    CONSTRAINT fk_operacao_oportunidade FOREIGN KEY (oportunidade_id) REFERENCES oportunidade_investimento (id),
    CONSTRAINT uq_operacao_credora_contrato UNIQUE (empresa_credora_id, contrato_id),
    CONSTRAINT chk_operacao_status CHECK (status IN ('ASSOCIADA', 'ENCERRADA'))
);

CREATE INDEX idx_operacao_credora_status ON operacao_financiada (empresa_credora_id, status, data_criacao DESC);

COMMENT ON TABLE operacao_financiada IS
    'Associacao operacional credora -> contrato (carteira, Sprint 17). contrato_id e referencia logica ao modulo contratos; sem movimentacao financeira real.';

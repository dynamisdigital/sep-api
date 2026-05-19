-- =============================================================================
-- V17 — Modulo Credito Open Finance (Sprint 9 — Epic 6 parte 2)
-- =============================================================================
-- Persistencia do ciclo de consentimento Open Finance Brasil (via Celcoin/
-- Finansystech) e snapshot consolidado de movimentacao bancaria usado pelo
-- motor de credito (RegraOpenFinanceMovimentacao).
--
-- Tabelas:
--   - consentimento_open_finance   (N:1 com proposta — ciclo de consentimento)
--   - movimentacao_open_finance    (N:1 com consentimento — snapshot consolidado)
--
-- Decisoes regulatorias / LGPD:
--   - Nenhuma FK usa ON DELETE CASCADE — preserva trilha auditavel (CMN 4.656/
--     2018 + LGPD retencao minima 5 anos para credito).
--   - Indice unico parcial em (proposta_id) WHERE status = 'PENDENTE' garante
--     no maximo 1 consentimento ativo por proposta; preserva historico de
--     consentimentos negados/expirados de tentativas anteriores.
--   - payload_consolidado e JSONB sanitizado (snapshot agregado), NAO extrato
--     bruto transacional — politica documentada em OPEN-FINANCE.md.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Consentimento Open Finance (N:1 com proposta)
-- -----------------------------------------------------------------------------
CREATE TABLE consentimento_open_finance (
    id UUID PRIMARY KEY,
    proposta_id UUID NOT NULL,
    tomador_id UUID NOT NULL,
    status VARCHAR(40) NOT NULL,
    url_autorizacao VARCHAR(1000),
    id_externo_celcoin VARCHAR(255),
    data_inicio TIMESTAMP WITH TIME ZONE NOT NULL,
    data_autorizacao TIMESTAMP WITH TIME ZONE,
    data_expiracao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_consentimento_of_proposta FOREIGN KEY (proposta_id) REFERENCES proposta_credito (id),
    CONSTRAINT fk_consentimento_of_tomador FOREIGN KEY (tomador_id) REFERENCES usuario (id),
    CONSTRAINT chk_consentimento_of_status CHECK (status IN ('PENDENTE', 'AUTORIZADO', 'NEGADO', 'EXPIRADO'))
);

CREATE INDEX idx_consentimento_of_proposta ON consentimento_open_finance (proposta_id, data_inicio DESC);
CREATE INDEX idx_consentimento_of_tomador ON consentimento_open_finance (tomador_id);
CREATE INDEX idx_consentimento_of_externo ON consentimento_open_finance (id_externo_celcoin);

-- Unique parcial: no maximo 1 consentimento PENDENTE por proposta.
-- Permite multiplos NEGADO/EXPIRADO historicos sem violar a constraint.
CREATE UNIQUE INDEX uq_consentimento_of_proposta_pendente
    ON consentimento_open_finance (proposta_id)
    WHERE status = 'PENDENTE';

COMMENT ON TABLE consentimento_open_finance IS 'Ciclo de consentimento Open Finance Brasil (Sprint 9). Opt-in obrigatorio do tomador (LGPD + Resolucao BCB 32/2020).';
COMMENT ON COLUMN consentimento_open_finance.status IS 'PENDENTE | AUTORIZADO | NEGADO | EXPIRADO — apenas AUTORIZADO habilita consulta de movimentacao.';
COMMENT ON COLUMN consentimento_open_finance.id_externo_celcoin IS 'Identificador do consentimento no Celcoin Finansystech — chave de correlacao do callback.';

-- -----------------------------------------------------------------------------
-- 2. Movimentacao Open Finance (N:1 com consentimento — snapshot consolidado)
-- -----------------------------------------------------------------------------
CREATE TABLE movimentacao_open_finance (
    id UUID PRIMARY KEY,
    consentimento_id UUID NOT NULL,
    proposta_id UUID NOT NULL,
    payload_consolidado JSONB NOT NULL,
    media_entradas_mensal NUMERIC(15, 2),
    media_saidas_mensal NUMERIC(15, 2),
    saldo_medio NUMERIC(15, 2),
    numero_meses_avaliados INTEGER,
    data_recebimento TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_movimentacao_of_consentimento FOREIGN KEY (consentimento_id) REFERENCES consentimento_open_finance (id),
    CONSTRAINT fk_movimentacao_of_proposta FOREIGN KEY (proposta_id) REFERENCES proposta_credito (id),
    CONSTRAINT chk_movimentacao_of_meses CHECK (numero_meses_avaliados IS NULL OR numero_meses_avaliados >= 0)
);

CREATE INDEX idx_movimentacao_of_proposta ON movimentacao_open_finance (proposta_id, data_recebimento DESC);
CREATE INDEX idx_movimentacao_of_consentimento ON movimentacao_open_finance (consentimento_id, data_recebimento DESC);

COMMENT ON TABLE movimentacao_open_finance IS 'Snapshot consolidado de movimentacao bancaria (Sprint 9). NAO contem extrato bruto transacional — apenas agregados usados pela RegraOpenFinanceMovimentacao.';
COMMENT ON COLUMN movimentacao_open_finance.payload_consolidado IS 'Payload Celcoin/Finansystech sanitizado em JSONB — politica de retencao LGPD documentada em OPEN-FINANCE.md.';

-- =============================================================================
-- V51 — Referencia Pix de recebimento + evolucao de pix_recebimento (Sprint 21 — Epic 15)
-- =============================================================================
-- A conciliacao automatica de recebimento Pix depende de um identificador
-- deterministico (txid) gerado pelo SEP e devolvido no webhook RECEBIMENTO_PIX.
-- A referencia liga esse txid a uma parcela; o recebimento ganha vinculo de
-- conciliacao (referencia/parcela/recebimento de cobranca + motivo de divergencia).
--
-- Decisoes:
--   - Sem FK fisica para cobranca: isolamento DDD por ports (mesmo padrao da
--     Sprint 20 / pix_transferencia).
--   - txid UNIQUE; provider_referencia_id UNIQUE parcial; no maximo uma
--     referencia ATIVA por parcela (UNIQUE parcial).
--   - Minimizacao: sem payload bruto; sem chave Pix/dado bancario sensivel
--     alem do necessario (codigo copia-cola eh do proprio recebedor SEP).
-- =============================================================================

CREATE TABLE pix_referencia_recebimento (
    id UUID PRIMARY KEY,
    parcela_id UUID NOT NULL,
    contrato_id UUID NOT NULL,
    tomador_id UUID NOT NULL,
    valor_esperado NUMERIC(19, 2) NOT NULL,
    txid VARCHAR(140) NOT NULL,
    provider_referencia_id VARCHAR(140),
    codigo_copia_cola VARCHAR(2000),
    status VARCHAR(40) NOT NULL,
    correlation_id VARCHAR(100),
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT chk_pix_ref_receb_status CHECK (
        status IN ('ATIVA', 'PAGA', 'EXPIRADA', 'CANCELADA', 'DIVERGENTE')
    ),
    CONSTRAINT chk_pix_ref_receb_valor CHECK (valor_esperado > 0)
);

CREATE UNIQUE INDEX uq_pix_ref_receb_txid ON pix_referencia_recebimento (txid);

CREATE UNIQUE INDEX uq_pix_ref_receb_provider_id
    ON pix_referencia_recebimento (provider_referencia_id)
    WHERE provider_referencia_id IS NOT NULL;

-- No maximo uma referencia ATIVA por parcela.
CREATE UNIQUE INDEX uq_pix_ref_receb_parcela_ativa
    ON pix_referencia_recebimento (parcela_id)
    WHERE status = 'ATIVA';

CREATE INDEX idx_pix_ref_receb_parcela ON pix_referencia_recebimento (parcela_id);

COMMENT ON TABLE pix_referencia_recebimento IS 'Referencia Pix (txid) de uma parcela para conciliacao automatica de recebimento (Sprint 21).';
COMMENT ON COLUMN pix_referencia_recebimento.txid IS 'Identificador deterministico gerado pelo SEP; correlaciona o webhook de recebimento de volta a parcela.';

-- -----------------------------------------------------------------------------
-- Evolucao de pix_recebimento: vinculo de conciliacao
-- -----------------------------------------------------------------------------
ALTER TABLE pix_recebimento
    ADD COLUMN referencia_id UUID,
    ADD COLUMN parcela_id UUID,
    ADD COLUMN recebimento_cobranca_id UUID,
    ADD COLUMN motivo_divergencia VARCHAR(240);

COMMENT ON COLUMN pix_recebimento.referencia_id IS 'PixReferenciaRecebimento correlacionada (Sprint 21).';
COMMENT ON COLUMN pix_recebimento.recebimento_cobranca_id IS 'Recebimento de cobranca gerado pela baixa (Sprint 21); sem FK fisica.';

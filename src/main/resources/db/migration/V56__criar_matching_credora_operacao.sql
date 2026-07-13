-- =============================================================================
-- V56 — Sprint 30 Task 30.2: matching assistido credora-operacao (Epic 15)
-- =============================================================================
-- Cria matching_credora_operacao: sugestao persistida de matching entre a
-- credora dona e uma operacao financiada da propria carteira pronta para
-- receber aporte assistido. O sistema sugere; a decisao (CONFIRMADA/REJEITADA)
-- e sempre de financeiro/admin com step-up estrito — nada e confirmado
-- automaticamente e nenhum aporte/Pix e disparado pela confirmacao.
--
-- Decisoes:
--   - UNIQUE parcial (empresa_credora_id, operacao_id) para status ativos
--     (SUGERIDA/CONFIRMADA): impede duplicidade ativa do par no banco. O
--     bloqueio de re-sugestao apos REJEITADA e regra de geracao (aplicacao),
--     mantendo o banco flexivel se o produto mudar essa decisao.
--   - FKs internas para empresa_credora e operacao_financiada SEM ON DELETE
--     CASCADE (trilha auditavel — CMN 4.656/2018 + LGPD).
--   - criterios_snapshot congela os criterios de elegibilidade atendidos na
--     sugestao (codigos funcionais separados por ';'; sem PII/score/payload).
--   - motivo_decisao_sanitizado guarda somente motivo tratado.
-- =============================================================================

CREATE TABLE matching_credora_operacao (
    id UUID PRIMARY KEY,
    empresa_credora_id UUID NOT NULL,
    operacao_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    valor_elegivel NUMERIC(19, 2) NOT NULL,
    criterios_snapshot VARCHAR(500) NOT NULL,
    decidido_por_usuario_id UUID,
    motivo_decisao_sanitizado VARCHAR(255),
    data_decisao TIMESTAMP WITH TIME ZONE,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT fk_matching_empresa_credora FOREIGN KEY (empresa_credora_id) REFERENCES empresa_credora (id),
    CONSTRAINT fk_matching_operacao FOREIGN KEY (operacao_id) REFERENCES operacao_financiada (id),
    CONSTRAINT chk_matching_status CHECK (status IN ('SUGERIDA', 'CONFIRMADA', 'REJEITADA')),
    CONSTRAINT chk_matching_valor_elegivel CHECK (valor_elegivel > 0)
);

-- Duplicidade ativa do par bloqueada no banco; historico REJEITADA nao conflita.
CREATE UNIQUE INDEX uq_matching_par_ativo ON matching_credora_operacao (empresa_credora_id, operacao_id)
    WHERE status IN ('SUGERIDA', 'CONFIRMADA');

-- Lookup de pares existentes na geracao em lote (qualquer status).
CREATE INDEX idx_matching_par ON matching_credora_operacao (empresa_credora_id, operacao_id);

-- FK lookup por operacao.
CREATE INDEX idx_matching_operacao ON matching_credora_operacao (operacao_id);

-- Listagem operacional de sugestoes por status.
CREATE INDEX idx_matching_status_criacao ON matching_credora_operacao (status, data_criacao);

COMMENT ON TABLE matching_credora_operacao IS 'Sugestao de matching credora-operacao (Sprint 30). Decisao sempre assistida por financeiro/admin com step-up estrito; sem aporte/Pix automatico.';
COMMENT ON COLUMN matching_credora_operacao.criterios_snapshot IS 'Criterios de elegibilidade atendidos na sugestao (codigos separados por ;); sem PII, score ou payload.';
COMMENT ON COLUMN matching_credora_operacao.motivo_decisao_sanitizado IS 'Motivo da decisao ja sanitizado; texto bruto do operador nao e persistido.';

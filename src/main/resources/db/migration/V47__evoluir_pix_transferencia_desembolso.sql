-- =============================================================================
-- V47 — Evolucao de pix_transferencia para desembolso assistido (Sprint 20 — Epic 15)
-- =============================================================================
-- A foundation (V45) modelou a transferencia de saida sem vinculo de negocio. A
-- Sprint 20 introduz o desembolso assistido: a transferencia passa a referenciar
-- o contrato/proposta/tomador de origem e a carregar o tipo da operacao.
--
-- Decisoes:
--   - Colunas NULLABLE: V45 nao gerou transferencias de desembolso; linhas
--     legadas (se houver) ficam sem vinculo. Apenas desembolsos novos preenchem.
--   - Sem FK fisica para contrato/proposta/tomador: modulos isolados por ports
--     (DDD); integridade referencial garantida na aplicacao (mesma postura da
--     foundation V45 e do escrow).
--   - Chave Pix destino NAO persistida em claro (minimizacao — CMN/LGPD): apenas
--     hash SHA-256 (consistencia idempotente) e mascara (resposta/auditoria).
--   - UNIQUE parcial impede mais de um desembolso "ocupando" o mesmo contrato
--     (CRIADA/SOLICITADA/PROCESSANDO/CONCLUIDA). FALHOU/CANCELADA liberam retry.
-- =============================================================================

ALTER TABLE pix_transferencia
    ADD COLUMN contrato_id UUID,
    ADD COLUMN proposta_id UUID,
    ADD COLUMN tomador_id UUID,
    ADD COLUMN tipo_transferencia VARCHAR(40),
    ADD COLUMN chave_destino_hash VARCHAR(64),
    ADD COLUMN chave_destino_mascara VARCHAR(80);

ALTER TABLE pix_transferencia
    ADD CONSTRAINT chk_pix_transferencia_tipo CHECK (
        tipo_transferencia IS NULL OR tipo_transferencia IN ('DESEMBOLSO_CONTRATO')
    );

ALTER TABLE pix_transferencia
    ADD CONSTRAINT chk_pix_transferencia_chave_hash CHECK (
        chave_destino_hash IS NULL OR chave_destino_hash ~ '^[a-fA-F0-9]{64}$'
    );

CREATE INDEX idx_pix_transferencia_contrato_status ON pix_transferencia (contrato_id, status);

-- Um unico desembolso pode ocupar o contrato por vez.
CREATE UNIQUE INDEX uq_pix_transferencia_contrato_ocupado
    ON pix_transferencia (contrato_id)
    WHERE contrato_id IS NOT NULL
      AND status IN ('CRIADA', 'SOLICITADA', 'PROCESSANDO', 'CONCLUIDA');

COMMENT ON COLUMN pix_transferencia.contrato_id IS 'Contrato de origem do desembolso (Sprint 20). Sem FK fisica — isolamento DDD por ports.';
COMMENT ON COLUMN pix_transferencia.chave_destino_hash IS 'SHA-256 hex da chave Pix destino normalizada. Chave em claro nunca eh persistida.';
COMMENT ON COLUMN pix_transferencia.chave_destino_mascara IS 'Mascara da chave Pix destino para resposta/auditoria.';

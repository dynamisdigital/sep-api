-- =============================================================================
-- V26 — Sprint 12 Task 12.3: taxa juros mensal em proposta_credito
-- =============================================================================
-- Adiciona a coluna {@code taxa_juros_mensal} pra persistir a condicao financeira
-- aprovada da proposta. Sprint 12 Task 12.3 usa essa taxa pra calcular a agenda
-- de pagamento; quando ausente (registros legados ou ainda nao aprovados),
-- cobranca aplica {@code app.cobranca.taxa-juros-mensal-default} como fallback
-- registrado em log warn.
--
-- Sprint posterior (taxa por decisao manual/motor) deve popular a coluna ANTES
-- da assinatura para garantir que a agenda use a taxa aprovada — nao a default.
-- =============================================================================

ALTER TABLE proposta_credito
    ADD COLUMN taxa_juros_mensal NUMERIC(8, 6);

ALTER TABLE proposta_credito
    ADD CONSTRAINT chk_proposta_credito_taxa CHECK (
        taxa_juros_mensal IS NULL OR taxa_juros_mensal >= 0
    );

COMMENT ON COLUMN proposta_credito.taxa_juros_mensal IS
    'Taxa de juros remuneratorios mensal aprovada (decimal — 0.02 = 2%). NULL aceita registros legados; Sprint posterior persiste explicitamente.';

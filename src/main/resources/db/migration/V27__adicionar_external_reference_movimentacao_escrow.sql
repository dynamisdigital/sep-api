-- =============================================================================
-- V27 — Sprint 12 Task 12.4: rastreabilidade de movimentacao_escrow
-- =============================================================================
-- Adiciona {@code external_reference_id} pra correlacionar uma {@code
-- movimentacao_escrow} a entidades externas ao escrow (Sprint 12: id do
-- {@code recebimento}; futuras: Pix txid, contrato, etc.). Nullable —
-- registros anteriores nao tem referencia.
-- =============================================================================

ALTER TABLE movimentacao_escrow
    ADD COLUMN external_reference_id UUID;

CREATE INDEX idx_movimentacao_external_ref
    ON movimentacao_escrow (external_reference_id) WHERE external_reference_id IS NOT NULL;

COMMENT ON COLUMN movimentacao_escrow.external_reference_id IS
    'Id da entidade externa que originou a movimentacao (Sprint 12: recebimento.id; futuras: txid Pix etc.).';

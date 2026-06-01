-- =============================================================================
-- V49 — UNIQUE parcial de external_id em pix_transferencia (Sprint 20 Task 20.4)
-- =============================================================================
-- O webhook STATUS_TRANSFERENCIA reconcilia a transferencia por external_id
-- (id do provider). A coluna nasceu em V45 sem unicidade; uma duplicidade por
-- corrida/replay/bug do provider faria findByExternalId estourar
-- IncorrectResultSizeDataAccessException e o webhook falhar em vez de reconciliar.
--
-- Garantia: o id externo do provider eh unico quando presente. Parcial (WHERE NOT
-- NULL) porque transferencias ainda em CRIADA nao tem external_id.
-- =============================================================================

CREATE UNIQUE INDEX uq_pix_transferencia_external_id
    ON pix_transferencia (external_id)
    WHERE external_id IS NOT NULL;

-- =============================================================================
-- V18 — Sprint 9 fix code review Task 9.3: unique consentimento_id em
-- movimentacao_open_finance.
-- =============================================================================
-- Garante 1 snapshot consolidado por consentimento Open Finance — callbacks
-- duplicados tardios nao geram snapshots redundantes nem disparam
-- OpenFinanceDadosRecebidosEvent duplicado (Task 9.4 reavaliaria score 2x).
-- Use case ConsultarMovimentacaoOpenFinanceUseCase detecta corrida via
-- DataIntegrityViolationException + retorna snapshot existente.
-- =============================================================================

ALTER TABLE movimentacao_open_finance
    ADD CONSTRAINT uq_movimentacao_of_consentimento UNIQUE (consentimento_id);

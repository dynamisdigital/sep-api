-- =============================================================================
-- V28 — Sprint 12 Task 12.4 (fix code review): unique constraints no escrow
-- =============================================================================
-- Sem UNIQUE em conta_escrow.titular nem em wallet.proposta_id, duas threads
-- concorrentes podiam criar contas/wallets duplicadas durante a primeira
-- movimentacao. Fix:
--
--   conta_escrow.titular     UNIQUE (full) — ha apenas uma conta tecnica por
--                                            titular (Sprint 12: SEP-COBRANCA).
--   wallet(proposta_id)      UNIQUE PARCIAL pra proposta_id IS NOT NULL — uma
--                                            wallet por proposta.
-- =============================================================================

CREATE UNIQUE INDEX uq_conta_escrow_titular ON conta_escrow (titular);

-- Substitui o indice nao-unico criado em V2.
DROP INDEX IF EXISTS idx_wallet_proposta;
CREATE UNIQUE INDEX uq_wallet_proposta ON wallet (proposta_id) WHERE proposta_id IS NOT NULL;

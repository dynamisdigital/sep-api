-- =============================================================================
-- V31 — Sprint 13 Task 13.6: Suporte a agenda substituta (renegociacao)
-- =============================================================================
-- Renegociacao aceita gera nova AgendaPagamento que substitui a anterior pro
-- mesmo contrato. A constraint UNIQUE em contrato_id (V25) impede duas
-- agendas, entao migramos pra UNIQUE parcial WHERE ativa=true — garante que
-- apenas uma agenda esta vigente por contrato, mantendo o historico.
--
-- Novos campos em agenda_pagamento:
--   - ativa BOOLEAN NOT NULL DEFAULT TRUE — agenda corrente do contrato
--   - agenda_substituida_id UUID NULL REFERENCES agenda_pagamento(id) — link
--     pra agenda anterior quando esta foi gerada por renegociacao
--
-- Decisoes:
--   - FK sem CASCADE (LGPD/CMN 4.656 — retencao agenda anterior).
--   - Constraint chk_substituida_diferente garante que agenda nao se referencia
--     a si mesma.
--   - UNIQUE parcial uq_agenda_contrato_ativa substitui o UNIQUE absoluto
--     de V25; rollback nao necessario porque V25 nunca rodou em prod com 2
--     agendas (impossivel).
-- =============================================================================

ALTER TABLE agenda_pagamento
    ADD COLUMN ativa BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN agenda_substituida_id UUID NULL,
    ADD CONSTRAINT fk_agenda_substituida
        FOREIGN KEY (agenda_substituida_id) REFERENCES agenda_pagamento (id),
    ADD CONSTRAINT chk_substituida_diferente
        CHECK (agenda_substituida_id IS NULL OR agenda_substituida_id <> id);

-- Remove UNIQUE absoluto (criado em V25) e substitui por unique parcial.
ALTER TABLE agenda_pagamento DROP CONSTRAINT agenda_pagamento_contrato_id_key;

CREATE UNIQUE INDEX uq_agenda_contrato_ativa
    ON agenda_pagamento (contrato_id)
    WHERE ativa = TRUE;

COMMENT ON COLUMN agenda_pagamento.ativa IS 'Apenas uma agenda ativa por contrato (Sprint 13 — renegociacao gera nova ativa, marca antiga inativa).';
COMMENT ON COLUMN agenda_pagamento.agenda_substituida_id IS 'FK pra agenda anterior quando esta foi gerada por renegociacao (Sprint 13).';

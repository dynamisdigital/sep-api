-- =============================================================================
-- V25 — Modulo Cobranca (Sprint 12 — Epic 7 parte 3)
-- =============================================================================
-- Cria a base persistente do modulo cobranca: agenda de pagamento, parcelas
-- planejadas e recebimentos manuais. Disparado por ContratoAssinadoEvent
-- (Sprint 11) e integrado com o modulo escrow para registrar movimentacao
-- segregada (Resolucao CMN 4.656/2018 Art. 9 + Art. 11).
--
-- Tabelas:
--   - agenda_pagamento     (agregado raiz — 1:1 com contrato)
--   - parcela_cobranca     (N:1 com agenda; numero unico por agenda)
--   - recebimento          (N:1 com parcela; idempotency_key UNIQUE global)
--
-- Decisoes regulatorias:
--   - Nenhuma FK usa ON DELETE CASCADE — preserva trilha auditavel de
--     parcelas/recebimentos mesmo apos delecao indevida de contrato (CMN
--     4.656/2018 + LGPD: retencao minima de 5 anos pra operacoes financeiras).
--   - agenda_pagamento tem UNIQUE em contrato_id (relacao 1:1 forte).
--   - recebimento tem UNIQUE em idempotency_key como defesa final contra
--     duplicidade de pagamento (Sprint 12 Task 12.4).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Agenda de pagamento (agregado raiz)
-- -----------------------------------------------------------------------------
CREATE TABLE agenda_pagamento (
    id UUID PRIMARY KEY,
    contrato_id UUID NOT NULL UNIQUE,
    numero_parcelas INTEGER NOT NULL,
    valor_total NUMERIC(15, 2) NOT NULL,
    data_geracao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT fk_agenda_pagamento_contrato FOREIGN KEY (contrato_id) REFERENCES contrato (id),
    CONSTRAINT chk_agenda_numero_parcelas CHECK (numero_parcelas > 0),
    CONSTRAINT chk_agenda_valor_total CHECK (valor_total >= 0)
);

CREATE INDEX idx_agenda_pagamento_contrato ON agenda_pagamento (contrato_id);

COMMENT ON TABLE agenda_pagamento IS 'Agenda de parcelas gerada apos contrato assinado (Sprint 12). 1:1 com contrato.';

-- -----------------------------------------------------------------------------
-- 2. Parcela cobranca (N:1 com agenda)
-- -----------------------------------------------------------------------------
CREATE TABLE parcela_cobranca (
    id UUID PRIMARY KEY,
    agenda_id UUID NOT NULL,
    numero INTEGER NOT NULL,
    principal NUMERIC(15, 2) NOT NULL,
    juros NUMERIC(15, 2) NOT NULL,
    multa NUMERIC(15, 2) NOT NULL,
    encargos NUMERIC(15, 2) NOT NULL,
    data_vencimento DATE NOT NULL,
    status VARCHAR(40) NOT NULL,
    CONSTRAINT fk_parcela_cobranca_agenda FOREIGN KEY (agenda_id) REFERENCES agenda_pagamento (id),
    CONSTRAINT uq_parcela_cobranca_agenda_numero UNIQUE (agenda_id, numero),
    CONSTRAINT chk_parcela_numero CHECK (numero > 0),
    CONSTRAINT chk_parcela_principal CHECK (principal >= 0),
    CONSTRAINT chk_parcela_juros CHECK (juros >= 0),
    CONSTRAINT chk_parcela_multa CHECK (multa >= 0),
    CONSTRAINT chk_parcela_encargos CHECK (encargos >= 0),
    CONSTRAINT chk_parcela_status CHECK (
        status IN ('PENDENTE', 'PARCIALMENTE_PAGA', 'PAGA', 'ATRASADA', 'INADIMPLENTE')
    )
);

CREATE INDEX idx_parcela_agenda ON parcela_cobranca (agenda_id);
CREATE INDEX idx_parcela_status_vencimento ON parcela_cobranca (status, data_vencimento);

COMMENT ON TABLE parcela_cobranca IS 'Parcela individual da agenda (Sprint 12). Composicao: principal + juros + multa + encargos.';
COMMENT ON COLUMN parcela_cobranca.status IS 'PENDENTE | PARCIALMENTE_PAGA | PAGA | ATRASADA | INADIMPLENTE (reservado Sprint 13).';

-- -----------------------------------------------------------------------------
-- 3. Recebimento (N:1 com parcela)
-- -----------------------------------------------------------------------------
CREATE TABLE recebimento (
    id UUID PRIMARY KEY,
    parcela_id UUID NOT NULL,
    valor_recebido NUMERIC(15, 2) NOT NULL,
    data_recebimento TIMESTAMP WITH TIME ZONE NOT NULL,
    meio_pagamento VARCHAR(40) NOT NULL,
    identificador_externo VARCHAR(100),
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    observacao VARCHAR(500),
    registrado_por UUID NOT NULL,
    CONSTRAINT fk_recebimento_parcela FOREIGN KEY (parcela_id) REFERENCES parcela_cobranca (id),
    CONSTRAINT chk_recebimento_valor CHECK (valor_recebido > 0)
);

CREATE INDEX idx_recebimento_parcela ON recebimento (parcela_id);
CREATE INDEX idx_recebimento_data ON recebimento (data_recebimento DESC);

COMMENT ON TABLE recebimento IS 'Registro de pagamento manual aplicado a uma parcela (Sprint 12). idempotency_key UNIQUE = defesa final contra duplicidade.';

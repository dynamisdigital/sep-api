-- =============================================================================
-- V14 — Modulo Credito (Sprint 8 — Epic 6 parte 1)
-- =============================================================================
-- Cria a base persistente da analise de credito interna do produto SEP conforme
-- Resolucao CMN 4.656/2018 Art. 9 (analise de credito) com trilha auditavel
-- completa do parecer.
--
-- Tabelas:
--   - proposta_credito          (agregado raiz)
--   - score_interno             (1:1 — produzido pelo motor de regras)
--   - regra_credito_avaliada    (N:1 — snapshot de cada regra)
--   - parecer_credito           (N:1 — parecer manual versionado)
--   - decisao_credito           (1:1 — decisao final consolidada)
--
-- Decisoes regulatorias:
--   - Nenhuma FK usa ON DELETE CASCADE em dados de credito — preserva trilha
--     auditavel mesmo apos delecao do tomador (CMN 4.656/2018 + LGPD: retencao
--     minima de 5 anos pra operacoes de credito).
--   - score_interno e decisao_credito tem UNIQUE em proposta_id (relacao 1:1
--     forte).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Proposta de credito (agregado raiz)
-- -----------------------------------------------------------------------------
CREATE TABLE proposta_credito (
    id UUID PRIMARY KEY,
    tomador_id UUID NOT NULL,
    solicitacao_onboarding_id UUID NOT NULL,
    tipo_operacao VARCHAR(40) NOT NULL,
    valor_solicitado NUMERIC(15, 2) NOT NULL,
    moeda VARCHAR(3) NOT NULL,
    prazo_meses INTEGER NOT NULL,
    status VARCHAR(40) NOT NULL,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT fk_proposta_credito_tomador FOREIGN KEY (tomador_id) REFERENCES usuario (id),
    CONSTRAINT fk_proposta_credito_onboarding FOREIGN KEY (solicitacao_onboarding_id) REFERENCES solicitacao_onboarding (id),
    CONSTRAINT chk_proposta_credito_status CHECK (status IN (
        'EM_ANALISE', 'PRE_APROVADA', 'APROVADA', 'REJEITADA', 'PENDENCIA'
    )),
    CONSTRAINT chk_proposta_credito_tipo CHECK (tipo_operacao IN ('CAPITAL_GIRO', 'OUTROS')),
    CONSTRAINT chk_proposta_credito_valor CHECK (valor_solicitado > 0),
    CONSTRAINT chk_proposta_credito_prazo CHECK (prazo_meses > 0),
    CONSTRAINT chk_proposta_credito_moeda CHECK (char_length(moeda) = 3)
);

CREATE INDEX idx_proposta_credito_status ON proposta_credito (status);
CREATE INDEX idx_proposta_credito_tomador ON proposta_credito (tomador_id, data_criacao DESC);
CREATE INDEX idx_proposta_credito_onboarding ON proposta_credito (solicitacao_onboarding_id);

COMMENT ON TABLE proposta_credito IS 'Solicitacao de credito (CMN 4.656/2018 Art. 9). Agregado raiz do modulo credito.';
COMMENT ON COLUMN proposta_credito.status IS 'EM_ANALISE | PRE_APROVADA | APROVADA | REJEITADA | PENDENCIA — gerenciado pelo agregado.';

-- -----------------------------------------------------------------------------
-- 2. Score interno (1:1 com proposta)
-- -----------------------------------------------------------------------------
CREATE TABLE score_interno (
    id UUID PRIMARY KEY,
    proposta_id UUID NOT NULL UNIQUE,
    valor INTEGER NOT NULL,
    status_sugerido VARCHAR(40) NOT NULL,
    falhas INTEGER NOT NULL,
    pendencias INTEGER NOT NULL,
    data_calculo TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_score_interno_proposta FOREIGN KEY (proposta_id) REFERENCES proposta_credito (id),
    CONSTRAINT chk_score_interno_valor CHECK (valor BETWEEN 0 AND 1000),
    CONSTRAINT chk_score_interno_status CHECK (status_sugerido IN (
        'EM_ANALISE', 'PRE_APROVADA', 'APROVADA', 'REJEITADA', 'PENDENCIA'
    ))
);

CREATE INDEX idx_score_interno_proposta ON score_interno (proposta_id);

COMMENT ON TABLE score_interno IS 'Score produzido pelo MotorRegrasCredito (0-1000). Sobrescrito a cada nova execucao do motor.';

-- -----------------------------------------------------------------------------
-- 3. Regras avaliadas (N:1 com proposta — trilha auditavel)
-- -----------------------------------------------------------------------------
CREATE TABLE regra_credito_avaliada (
    id UUID PRIMARY KEY,
    proposta_id UUID NOT NULL,
    nome_regra VARCHAR(80) NOT NULL,
    resultado VARCHAR(20) NOT NULL,
    motivo VARCHAR(500),
    bloqueante BOOLEAN NOT NULL DEFAULT FALSE,
    data_avaliacao TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_regra_credito_proposta FOREIGN KEY (proposta_id) REFERENCES proposta_credito (id),
    CONSTRAINT chk_regra_credito_resultado CHECK (resultado IN ('PASSOU', 'FALHOU', 'PENDENTE'))
);

CREATE INDEX idx_regra_credito_proposta ON regra_credito_avaliada (proposta_id, data_avaliacao);

COMMENT ON TABLE regra_credito_avaliada IS 'Snapshot de cada regra avaliada — base auditavel de "por que aprovou/rejeitou".';
COMMENT ON COLUMN regra_credito_avaliada.bloqueante IS 'Regra bloqueante: FALHOU leva direto a REJEITADA, independente do score.';

-- -----------------------------------------------------------------------------
-- 4. Parecer credito (N:1 com proposta — versionado)
-- -----------------------------------------------------------------------------
CREATE TABLE parecer_credito (
    id UUID PRIMARY KEY,
    proposta_id UUID NOT NULL,
    parecerista_id UUID NOT NULL,
    decisao VARCHAR(20) NOT NULL,
    justificativa VARCHAR(1000) NOT NULL,
    score_motor_snapshot INTEGER,
    versao INTEGER NOT NULL,
    data_parecer TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_parecer_credito_proposta FOREIGN KEY (proposta_id) REFERENCES proposta_credito (id),
    CONSTRAINT fk_parecer_credito_parecerista FOREIGN KEY (parecerista_id) REFERENCES usuario (id),
    CONSTRAINT chk_parecer_credito_decisao CHECK (decisao IN ('APROVAR', 'REJEITAR', 'PENDENCIA')),
    CONSTRAINT chk_parecer_credito_versao CHECK (versao > 0),
    CONSTRAINT chk_parecer_credito_score CHECK (score_motor_snapshot IS NULL OR score_motor_snapshot BETWEEN 0 AND 1000),
    CONSTRAINT uq_parecer_credito_versao UNIQUE (proposta_id, versao)
);

CREATE INDEX idx_parecer_credito_proposta ON parecer_credito (proposta_id, versao DESC);
CREATE INDEX idx_parecer_credito_parecerista ON parecer_credito (parecerista_id);

COMMENT ON TABLE parecer_credito IS 'Parecer manual de operador FINANCEIRO (CMN 4.656/2018 Art. 9). Versionado pra preservar historico de reaberturas.';
COMMENT ON COLUMN parecer_credito.score_motor_snapshot IS 'Score do motor no momento do parecer — auditoria compara sugestao vs decisao manual.';

-- -----------------------------------------------------------------------------
-- 5. Decisao credito (1:1 — estado final consolidado)
-- -----------------------------------------------------------------------------
CREATE TABLE decisao_credito (
    id UUID PRIMARY KEY,
    proposta_id UUID NOT NULL UNIQUE,
    status_final VARCHAR(40) NOT NULL,
    origem VARCHAR(20) NOT NULL,
    score_motor INTEGER,
    parecer_id UUID,
    data_decisao TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_decisao_credito_proposta FOREIGN KEY (proposta_id) REFERENCES proposta_credito (id),
    CONSTRAINT fk_decisao_credito_parecer FOREIGN KEY (parecer_id) REFERENCES parecer_credito (id),
    CONSTRAINT chk_decisao_credito_status CHECK (status_final IN ('APROVADA', 'REJEITADA')),
    CONSTRAINT chk_decisao_credito_origem CHECK (origem IN ('MOTOR', 'MANUAL')),
    CONSTRAINT chk_decisao_credito_score CHECK (score_motor IS NULL OR score_motor BETWEEN 0 AND 1000),
    CONSTRAINT chk_decisao_credito_parecer_manual CHECK (
        (origem = 'MANUAL' AND parecer_id IS NOT NULL)
        OR (origem = 'MOTOR' AND parecer_id IS NULL)
    )
);

CREATE INDEX idx_decisao_credito_proposta ON decisao_credito (proposta_id);

COMMENT ON TABLE decisao_credito IS 'Decisao final consolidada — gravada quando proposta atinge APROVADA ou REJEITADA. Sprint 10 (Formalizacao) consome APROVADA pra iniciar contrato.';

-- =============================================================================
-- V30 — Sprint 13 Task 13.2: Inadimplencia, workflow, eventos e renegociacao
-- =============================================================================
-- Estende o modulo cobranca (Sprint 12) com a infraestrutura persistente do
-- workflow de cobranca, da trilha operacional de eventos por parcela e do
-- ciclo de renegociacao basica.
--
-- Tabelas novas:
--   - workflow_cobranca   (etapas configuraveis por dia de atraso)
--   - evento_cobranca     (historico de notificacoes e contatos por parcela)
--   - renegociacao        (proposta-aceite-recusa-expiracao por parcela)
--
-- Alteracoes em tabela existente:
--   - parcela_cobranca.chk_parcela_status amplia com EM_NEGOCIACAO e
--     RENEGOCIADA (StatusParcela Sprint 13).
--
-- Decisoes regulatorias e operacionais:
--   - FKs sem ON DELETE CASCADE — preserva trilha mesmo apos delecao indevida
--     de parcela (LGPD/CMN 4.656/2018: retencao minima 5 anos pra operacoes
--     financeiras).
--   - UNIQUE parcial uq_workflow_nome_dia_ativo garante que uma unica etapa
--     ativa exista por (nome, dia_atraso); historico desativado fica retido.
--   - UNIQUE parcial uq_renegociacao_parcela_ativa garante apenas uma proposta
--     PROPOSTA por parcela (CDC: evitar acordos sobrepostos).
--   - UNIQUE parcial uq_evento_notificacao_idempotencia previne envio duplicado
--     de mesma notificacao automatica no mesmo dia (canal+template).
--   - evento_cobranca NAO carrega corpo da mensagem nem dados pessoais
--     (LGPD: minimo necessario; templates/canal reconstroem contexto).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Workflow de cobranca — etapas por dia de atraso
-- -----------------------------------------------------------------------------
CREATE TABLE workflow_cobranca (
    id UUID PRIMARY KEY,
    nome VARCHAR(40) NOT NULL,
    dia_atraso INTEGER NOT NULL,
    notificacoes_csv VARCHAR(500) NOT NULL,
    flag_contato_manual BOOLEAN NOT NULL DEFAULT FALSE,
    escalonar_backoffice BOOLEAN NOT NULL DEFAULT FALSE,
    marcar_inadimplente BOOLEAN NOT NULL DEFAULT FALSE,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT chk_workflow_dia_atraso CHECK (dia_atraso >= 0)
);

CREATE UNIQUE INDEX uq_workflow_nome_dia_ativo
    ON workflow_cobranca (nome, dia_atraso)
    WHERE ativo = TRUE;

CREATE INDEX idx_workflow_nome_ativo ON workflow_cobranca (nome, ativo);

COMMENT ON TABLE workflow_cobranca IS 'Etapas configuraveis do workflow de cobranca (Sprint 13). Seed inicial vem do YAML em Task 13.4.';
COMMENT ON COLUMN workflow_cobranca.notificacoes_csv IS 'Lista CSV de nomes de template a disparar (ex: email-amigavel,sms-lembrete).';

-- -----------------------------------------------------------------------------
-- 2. Evento de cobranca — trilha operacional por parcela
-- -----------------------------------------------------------------------------
CREATE TABLE evento_cobranca (
    id UUID PRIMARY KEY,
    parcela_id UUID NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    canal VARCHAR(10),
    template VARCHAR(80),
    status VARCHAR(20) NOT NULL,
    dias_atraso INTEGER,
    descricao VARCHAR(500),
    registrado_por UUID,
    data_evento TIMESTAMP WITH TIME ZONE NOT NULL,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT fk_evento_cobranca_parcela FOREIGN KEY (parcela_id) REFERENCES parcela_cobranca (id),
    CONSTRAINT chk_evento_cobranca_tipo CHECK (tipo IN (
        'NOTIFICACAO_AUTOMATICA',
        'CONTATO_MANUAL',
        'RENEGOCIACAO_PROPOSTA',
        'RENEGOCIACAO_ACEITA',
        'RENEGOCIACAO_RECUSADA',
        'RENEGOCIACAO_EXPIRADA',
        'PARCELA_INADIMPLENTE'
    )),
    CONSTRAINT chk_evento_cobranca_canal CHECK (canal IS NULL OR canal IN ('EMAIL', 'SMS')),
    CONSTRAINT chk_evento_cobranca_status CHECK (status IN ('SUCESSO', 'FALHA'))
);

CREATE INDEX idx_evento_cobranca_parcela ON evento_cobranca (parcela_id, data_evento DESC);
CREATE INDEX idx_evento_cobranca_tipo_data ON evento_cobranca (tipo, data_evento DESC);

-- Idempotencia: nao reemitir notificacao automatica identica no mesmo dia.
-- Cobre apenas NOTIFICACAO_AUTOMATICA (parcial) pois contato manual e
-- renegociacao podem ter multiplos registros legitimos.
CREATE UNIQUE INDEX uq_evento_notificacao_idempotencia
    ON evento_cobranca (parcela_id, dias_atraso, canal, template)
    WHERE tipo = 'NOTIFICACAO_AUTOMATICA';

COMMENT ON TABLE evento_cobranca IS 'Historico operacional de cobranca por parcela (Sprint 13). Nao persiste corpo da mensagem.';

-- -----------------------------------------------------------------------------
-- 3. Renegociacao — proposta + aceite/recusa por parcela
-- -----------------------------------------------------------------------------
CREATE TABLE renegociacao (
    id UUID PRIMARY KEY,
    parcela_original_id UUID NOT NULL,
    agenda_original_id UUID NOT NULL,
    tomador_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    status_parcela_anterior VARCHAR(20) NOT NULL,
    novo_valor_parcela NUMERIC(15, 2) NOT NULL,
    novo_vencimento DATE NOT NULL,
    numero_parcelas INTEGER NOT NULL,
    desconto NUMERIC(15, 2) NOT NULL,
    justificativa VARCHAR(1000) NOT NULL,
    proposta_por UUID NOT NULL,
    data_proposta TIMESTAMP WITH TIME ZONE NOT NULL,
    data_expiracao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_decisao TIMESTAMP WITH TIME ZONE,
    agenda_substituta_id UUID,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT fk_renegociacao_parcela FOREIGN KEY (parcela_original_id) REFERENCES parcela_cobranca (id),
    CONSTRAINT fk_renegociacao_agenda FOREIGN KEY (agenda_original_id) REFERENCES agenda_pagamento (id),
    CONSTRAINT fk_renegociacao_agenda_substituta FOREIGN KEY (agenda_substituta_id) REFERENCES agenda_pagamento (id),
    CONSTRAINT chk_renegociacao_status CHECK (status IN ('PROPOSTA', 'ACEITA', 'RECUSADA', 'EXPIRADA')),
    CONSTRAINT chk_renegociacao_status_anterior CHECK (status_parcela_anterior IN ('ATRASADA', 'INADIMPLENTE')),
    CONSTRAINT chk_renegociacao_valor CHECK (novo_valor_parcela > 0),
    CONSTRAINT chk_renegociacao_parcelas CHECK (numero_parcelas > 0),
    CONSTRAINT chk_renegociacao_desconto CHECK (desconto >= 0),
    CONSTRAINT chk_renegociacao_expiracao CHECK (data_expiracao > data_proposta),
    CONSTRAINT chk_renegociacao_decisao_aceite CHECK (
        status <> 'ACEITA' OR (agenda_substituta_id IS NOT NULL AND data_decisao IS NOT NULL)
    ),
    CONSTRAINT chk_renegociacao_decisao_terminal CHECK (
        status NOT IN ('RECUSADA', 'EXPIRADA') OR data_decisao IS NOT NULL
    )
);

CREATE INDEX idx_renegociacao_tomador ON renegociacao (tomador_id);
CREATE INDEX idx_renegociacao_status_expiracao ON renegociacao (status, data_expiracao);

CREATE UNIQUE INDEX uq_renegociacao_parcela_ativa
    ON renegociacao (parcela_original_id)
    WHERE status = 'PROPOSTA';

COMMENT ON TABLE renegociacao IS 'Renegociacao basica de parcela atrasada/inadimplente (Sprint 13). Apenas uma proposta ativa por parcela.';

-- -----------------------------------------------------------------------------
-- 4. Amplia StatusParcela com EM_NEGOCIACAO e RENEGOCIADA
-- -----------------------------------------------------------------------------
ALTER TABLE parcela_cobranca DROP CONSTRAINT chk_parcela_status;

ALTER TABLE parcela_cobranca ADD CONSTRAINT chk_parcela_status CHECK (
    status IN ('PENDENTE', 'PARCIALMENTE_PAGA', 'PAGA', 'ATRASADA', 'INADIMPLENTE', 'EM_NEGOCIACAO', 'RENEGOCIADA')
);

COMMENT ON COLUMN parcela_cobranca.status IS 'PENDENTE | PARCIALMENTE_PAGA | PAGA | ATRASADA | INADIMPLENTE | EM_NEGOCIACAO | RENEGOCIADA (Sprint 13).';

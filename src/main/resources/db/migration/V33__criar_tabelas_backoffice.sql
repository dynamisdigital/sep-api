-- =============================================================================
-- V33 — Sprint 14 Task 14.1: Modulo backoffice (Epic 9)
-- =============================================================================
-- Tabelas novas:
--   - item_fila_operacional  (fila unica de situacoes que exigem intervencao humana)
--   - comentario_interno     (comentarios + justificativas N:1 com item)
--   - reprocesso             (registro auditavel de cada reprocesso manual)
--
-- Decisoes regulatorias e operacionais:
--   - Referencia ao objeto original eh logica (tipo_entidade + entidade_id) sem
--     FK fisica, pra evitar acoplamento ao schema dos modulos consumidos
--     (onboarding, credito, contratos, cobranca, webhook).
--   - UNIQUE parcial uq_item_ativo_por_entidade restringe duplicidade apenas a
--     itens ativos (ABERTO/EM_TRATAMENTO); apos resolucao um novo item pode
--     ser aberto pra mesma entidade.
--   - FK comentario_interno -> item_fila_operacional sem ON DELETE CASCADE
--     (preserva trilha LGPD/CMN 4.656 mesmo em delecao indevida).
--   - reprocesso.item_id eh nullable: reprocesso pode ser disparado fora do
--     contexto de item (ex.: webhook que nunca virou item).
--   - reprocesso indexado por (identificador_externo, data_disparo DESC) pra
--     suportar query de anti-abuso 3/24h da Task 14.4.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. item_fila_operacional
-- -----------------------------------------------------------------------------
CREATE TABLE item_fila_operacional (
    id UUID PRIMARY KEY,
    tipo VARCHAR(40) NOT NULL,
    prioridade VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    tipo_entidade VARCHAR(40) NOT NULL,
    entidade_id UUID NOT NULL,
    titulo VARCHAR(255) NOT NULL,
    descricao VARCHAR(4000),
    atribuido_a UUID,
    data_abertura TIMESTAMP WITH TIME ZONE NOT NULL,
    data_resolucao TIMESTAMP WITH TIME ZONE,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT chk_item_fila_tipo CHECK (tipo IN (
        'ONBOARDING_PENDENTE',
        'ONBOARDING_ERRO',
        'PROPOSTA_PENDENTE',
        'CONTRATO_NAO_ASSINADO',
        'COBRANCA_INADIMPLENTE',
        'WEBHOOK_FALHOU',
        'OUTRO'
    )),
    CONSTRAINT chk_item_fila_prioridade CHECK (prioridade IN ('BAIXA', 'MEDIA', 'ALTA', 'CRITICA')),
    CONSTRAINT chk_item_fila_status CHECK (status IN ('ABERTO', 'EM_TRATAMENTO', 'RESOLVIDO', 'IGNORADO')),
    CONSTRAINT chk_item_fila_tipo_entidade CHECK (tipo_entidade IN (
        'ONBOARDING',
        'PROPOSTA',
        'CONTRATO',
        'PARCELA_COBRANCA',
        'WEBHOOK_EVENT_LOG',
        'OUTRO'
    ))
);

CREATE UNIQUE INDEX uq_item_ativo_por_entidade
    ON item_fila_operacional (tipo, tipo_entidade, entidade_id)
    WHERE status IN ('ABERTO', 'EM_TRATAMENTO');

CREATE INDEX idx_item_fila_status_prioridade
    ON item_fila_operacional (status, prioridade, data_abertura);

CREATE INDEX idx_item_fila_atribuido
    ON item_fila_operacional (atribuido_a)
    WHERE atribuido_a IS NOT NULL;

CREATE INDEX idx_item_fila_tipo_data_abertura
    ON item_fila_operacional (tipo, data_abertura DESC);

COMMENT ON TABLE item_fila_operacional IS
    'Fila operacional do backoffice (Sprint 14). Idempotencia via UNIQUE parcial em itens ativos.';
COMMENT ON COLUMN item_fila_operacional.tipo_entidade IS
    'Categoria do objeto referenciado pelo par (tipo_entidade, entidade_id). Sem FK fisica.';

-- -----------------------------------------------------------------------------
-- 2. comentario_interno
-- -----------------------------------------------------------------------------
CREATE TABLE comentario_interno (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL,
    autor_id UUID NOT NULL,
    conteudo VARCHAR(10000) NOT NULL,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT fk_comentario_item FOREIGN KEY (item_id) REFERENCES item_fila_operacional (id)
);

CREATE INDEX idx_comentario_item ON comentario_interno (item_id, data_criacao ASC);

COMMENT ON TABLE comentario_interno IS
    'Comentarios internos e justificativas vinculados a um item da fila operacional (Sprint 14).';

-- -----------------------------------------------------------------------------
-- 3. reprocesso
-- -----------------------------------------------------------------------------
CREATE TABLE reprocesso (
    id UUID PRIMARY KEY,
    item_id UUID,
    tipo VARCHAR(20) NOT NULL,
    tipo_chamada VARCHAR(40),
    identificador_externo VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    resultado VARCHAR(4000),
    data_disparo TIMESTAMP WITH TIME ZONE NOT NULL,
    disparado_por UUID NOT NULL,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT fk_reprocesso_item FOREIGN KEY (item_id) REFERENCES item_fila_operacional (id),
    CONSTRAINT chk_reprocesso_tipo CHECK (tipo IN ('WEBHOOK', 'PROVIDER')),
    CONSTRAINT chk_reprocesso_tipo_chamada CHECK (
        tipo_chamada IS NULL OR tipo_chamada IN (
            'KYC', 'KYB', 'PLD', 'OPEN_FINANCE', 'ASSINATURA_DIGITAL'
        )
    ),
    CONSTRAINT chk_reprocesso_status CHECK (status IN ('PENDENTE', 'SUCESSO', 'FALHA')),
    CONSTRAINT chk_reprocesso_provider_exige_chamada CHECK (
        tipo = 'WEBHOOK' OR tipo_chamada IS NOT NULL
    )
);

CREATE INDEX idx_reprocesso_entidade_data
    ON reprocesso (identificador_externo, data_disparo DESC);

CREATE INDEX idx_reprocesso_item ON reprocesso (item_id) WHERE item_id IS NOT NULL;

COMMENT ON TABLE reprocesso IS
    'Registro auditavel de reprocessos manuais (Sprint 14). Anti-abuso via query em janela 24h.';

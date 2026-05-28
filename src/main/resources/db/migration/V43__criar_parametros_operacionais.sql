-- =============================================================================
-- V43 — Sprint 18 Task 18.4: parametros operacionais governados (Epic 11)
-- =============================================================================
-- Tabelas novas:
--   - parametro_operacional         (parametro governado, valor textual tipado, versionado)
--   - versao_parametro_operacional  (historico imutavel de cada alteracao)
--
-- Decisoes:
--   - valor armazenado como texto, validado conforme tipo (INTEGER/DECIMAL/BOOLEAN/STRING)
--     pelo dominio; chave unica.
--   - Cada alteracao incrementa parametro_operacional.versao e grava uma linha de historico
--     com valor anterior/novo, ator e justificativa (auditavel).
--   - FK versao -> parametro sem ON DELETE CASCADE (preserva trilha).
--   - Seed inicial reflete os defaults atuais em application.yml (app.credito.motor.* e
--     app.backoffice.verificador.*). Consumidores continuam lendo properties nesta sprint
--     (adocao incremental do ParametroOperacionalReader documentada em CREDORES/SEGURANCA).
-- =============================================================================

CREATE TABLE parametro_operacional (
    id UUID PRIMARY KEY,
    chave VARCHAR(120) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    valor VARCHAR(500) NOT NULL,
    descricao VARCHAR(500),
    ativo BOOLEAN NOT NULL,
    versao INTEGER NOT NULL,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT uq_parametro_chave UNIQUE (chave),
    CONSTRAINT chk_parametro_tipo CHECK (tipo IN ('INTEGER', 'DECIMAL', 'BOOLEAN', 'STRING'))
);

CREATE TABLE versao_parametro_operacional (
    id UUID PRIMARY KEY,
    parametro_id UUID NOT NULL,
    versao INTEGER NOT NULL,
    valor_anterior VARCHAR(500),
    valor_novo VARCHAR(500) NOT NULL,
    ator_id UUID NOT NULL,
    justificativa VARCHAR(500) NOT NULL,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT fk_versao_parametro FOREIGN KEY (parametro_id) REFERENCES parametro_operacional (id)
);

CREATE INDEX idx_versao_parametro ON versao_parametro_operacional (parametro_id, versao DESC);

COMMENT ON TABLE parametro_operacional IS
    'Parametros operacionais governados e versionados (Sprint 18). Valor textual validado por tipo no dominio.';

-- -----------------------------------------------------------------------------
-- Seed inicial (defaults atuais de application.yml)
-- -----------------------------------------------------------------------------
INSERT INTO parametro_operacional
    (id, chave, tipo, valor, descricao, ativo, versao, data_criacao, data_modificacao, criado_por, modificado_por)
VALUES
    (gen_random_uuid(), 'credito.valor.maximo.pf', 'DECIMAL', '50000.00',
        'Valor maximo de proposta para PF', true, 1, now(), now(), 'system', 'system'),
    (gen_random_uuid(), 'credito.valor.maximo.pj', 'DECIMAL', '200000.00',
        'Valor maximo de proposta para PJ', true, 1, now(), now(), 'system', 'system'),
    (gen_random_uuid(), 'credito.prazo.maximo.pf.meses', 'INTEGER', '12',
        'Prazo maximo em meses para PF', true, 1, now(), now(), 'system', 'system'),
    (gen_random_uuid(), 'credito.prazo.maximo.pj.meses', 'INTEGER', '24',
        'Prazo maximo em meses para PJ', true, 1, now(), now(), 'system', 'system'),
    (gen_random_uuid(), 'credito.score.pre-aprovacao', 'INTEGER', '700',
        'Score minimo para pre-aprovacao no motor de credito', true, 1, now(), now(), 'system', 'system'),
    (gen_random_uuid(), 'backoffice.proposta.pendente.horas', 'INTEGER', '24',
        'Limite (h) para proposta EM_ANALISE virar pendencia de backoffice', true, 1, now(), now(), 'system', 'system'),
    (gen_random_uuid(), 'backoffice.contrato.aceito.horas', 'INTEGER', '48',
        'Limite (h) para contrato ACEITO sem assinatura virar pendencia', true, 1, now(), now(), 'system', 'system'),
    (gen_random_uuid(), 'backoffice.webhook.pendente.horas', 'INTEGER', '1',
        'Limite (h) para webhook FALHOU/PENDENTE virar pendencia', true, 1, now(), now(), 'system', 'system');

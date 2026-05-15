-- =============================================================================
-- V11 — Tabelas KYB Empresa + PLD (Sprint 7 — Task 7.2)
-- =============================================================================
-- Modulo `onboarding` ampliado para KYB pessoa juridica e PLD (PF + PJ + reps).
-- Resolucao CMN 4.656/2018 + Lei 9.613/1998 (PLD).
--
-- Tabelas:
--   - kyb_empresa            (1:1 com solicitacao_onboarding tipo EMPRESA)
--   - consulta_cnpj          (1:1 com kyb_empresa; snapshot do KybProvider)
--   - representante_legal    (N:1 com kyb_empresa)
--   - consulta_pld           (N:1 com solicitacao_onboarding)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. KYB empresa
-- -----------------------------------------------------------------------------
CREATE TABLE kyb_empresa (
    id UUID PRIMARY KEY,
    solicitacao_id UUID NOT NULL UNIQUE,
    cnpj VARCHAR(14) NOT NULL,
    razao_social VARCHAR(255) NOT NULL,
    nome_fantasia VARCHAR(255),
    tipo_societario VARCHAR(20),
    porte VARCHAR(20),
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT fk_kyb_empresa_solicitacao
        FOREIGN KEY (solicitacao_id) REFERENCES solicitacao_onboarding (id) ON DELETE CASCADE,
    CONSTRAINT chk_kyb_empresa_tipo_societario CHECK (
        tipo_societario IS NULL OR tipo_societario IN ('LTDA', 'SA', 'EIRELI', 'MEI', 'OUTROS')
    ),
    CONSTRAINT chk_kyb_empresa_porte CHECK (
        porte IS NULL OR porte IN ('MEI', 'ME', 'EPP', 'MEDIO', 'GRANDE')
    )
);

CREATE INDEX idx_kyb_empresa_cnpj ON kyb_empresa (cnpj);

COMMENT ON TABLE kyb_empresa IS 'Agregado KYB PJ (CMN 4.656/2018). 1:1 com solicitacao_onboarding tipo EMPRESA.';
COMMENT ON COLUMN kyb_empresa.cnpj IS '14 digitos normalizados (sem mascara).';

-- -----------------------------------------------------------------------------
-- 2. Consulta CNPJ (resultado KybProvider)
-- -----------------------------------------------------------------------------
CREATE TABLE consulta_cnpj (
    id UUID PRIMARY KEY,
    kyb_empresa_id UUID NOT NULL UNIQUE,
    situacao_cadastral VARCHAR(20) NOT NULL,
    razao_social VARCHAR(255),
    nome_fantasia VARCHAR(255),
    cnae_principal VARCHAR(20),
    cnaes_secundarios TEXT,
    capital_social NUMERIC(19, 2),
    data_abertura DATE,
    payload_provider JSONB,
    data_consulta TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_consulta_cnpj_kyb_empresa
        FOREIGN KEY (kyb_empresa_id) REFERENCES kyb_empresa (id) ON DELETE CASCADE,
    CONSTRAINT chk_consulta_cnpj_situacao CHECK (situacao_cadastral IN (
        'ATIVA', 'SUSPENSA', 'INAPTA', 'BAIXADA', 'DESCONHECIDA'
    ))
);

COMMENT ON TABLE consulta_cnpj IS 'Snapshot da consulta KybProvider. Payload bruto JSONB nunca replicado em audit_log_seguranca.';
COMMENT ON COLUMN consulta_cnpj.payload_provider IS 'Payload cru do provider. NUNCA replicar em audit log ou logs publicos.';

-- -----------------------------------------------------------------------------
-- 3. Representante legal
-- -----------------------------------------------------------------------------
CREATE TABLE representante_legal (
    id UUID PRIMARY KEY,
    kyb_empresa_id UUID NOT NULL,
    nome VARCHAR(255) NOT NULL,
    cpf VARCHAR(11) NOT NULL,
    cargo VARCHAR(60),
    status_pld VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    data_registro TIMESTAMP WITH TIME ZONE NOT NULL,
    data_consulta_pld TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_representante_legal_kyb_empresa
        FOREIGN KEY (kyb_empresa_id) REFERENCES kyb_empresa (id) ON DELETE CASCADE,
    CONSTRAINT chk_representante_status_pld CHECK (status_pld IN ('PENDENTE', 'LIMPO', 'HIT'))
);

CREATE INDEX idx_representante_legal_kyb_empresa ON representante_legal (kyb_empresa_id);
CREATE INDEX idx_representante_legal_cpf ON representante_legal (cpf);

COMMENT ON TABLE representante_legal IS 'Representantes legais da PJ. CPF persistido inteiro (LGPD Art. 16); jamais exposto em logs/REST publicos.';

-- -----------------------------------------------------------------------------
-- 4. Consulta PLD (PF + PJ + representante x base)
-- -----------------------------------------------------------------------------
CREATE TABLE consulta_pld (
    id UUID PRIMARY KEY,
    solicitacao_id UUID NOT NULL,
    alvo_tipo VARCHAR(20) NOT NULL,
    alvo_documento VARCHAR(14) NOT NULL,
    base VARCHAR(20) NOT NULL,
    hit BOOLEAN NOT NULL DEFAULT FALSE,
    motivo TEXT,
    severidade VARCHAR(20),
    data_inclusao DATE,
    payload_provider JSONB,
    data_consulta TIMESTAMP WITH TIME ZONE NOT NULL,
    retencao_ate DATE NOT NULL,
    -- Sem ON DELETE CASCADE: trilha PLD precisa sobreviver a exclusoes operacionais da
    -- solicitacao (retencao minima 5 anos / LGPD Art. 16). Purge dedicado virara rotina
    -- explicita na Sprint 14+ (job de retencao), respeitando `retencao_ate`.
    CONSTRAINT fk_consulta_pld_solicitacao
        FOREIGN KEY (solicitacao_id) REFERENCES solicitacao_onboarding (id),
    CONSTRAINT chk_consulta_pld_alvo CHECK (alvo_tipo IN ('PESSOA', 'EMPRESA', 'REPRESENTANTE')),
    CONSTRAINT chk_consulta_pld_base CHECK (base IN ('COAF', 'OFAC', 'INTERPOL', 'MTE')),
    CONSTRAINT chk_consulta_pld_severidade CHECK (
        severidade IS NULL OR severidade IN ('BAIXA', 'MEDIA', 'ALTA')
    )
);

CREATE INDEX idx_consulta_pld_solicitacao ON consulta_pld (solicitacao_id);
CREATE INDEX idx_consulta_pld_documento ON consulta_pld (alvo_documento);
CREATE INDEX idx_consulta_pld_hit ON consulta_pld (solicitacao_id) WHERE hit = TRUE;

COMMENT ON TABLE consulta_pld IS 'Consulta PLD individual (alvo x base). Lei 9.613/1998. Retencao minima 5 anos (LGPD Art. 16) via retencao_ate.';
COMMENT ON COLUMN consulta_pld.payload_provider IS 'Payload cru do provider. NUNCA replicar em audit log ou logs publicos.';
COMMENT ON COLUMN consulta_pld.retencao_ate IS 'data_consulta + 5 anos. Job de purge futuro (Sprint 14+).';

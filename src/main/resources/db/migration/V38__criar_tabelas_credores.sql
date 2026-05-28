-- =============================================================================
-- V38 — Sprint 16 Task 16.2: Modulo credores (Epic 10) — foundation
-- =============================================================================
-- Tabelas novas:
--   - empresa_credora  (agregado raiz da jornada credora)
--   - perfil_credora   (perfil operacional 1:1 com empresa_credora)
--
-- Decisoes:
--   - Vinculo logico com usuario (usuario_id) e onboarding (onboarding_id) sem FK
--     fisica, pra nao acoplar o schema de credores aos modulos identity/onboarding
--     (mesma diretriz da V33/backoffice). Unicidade garantida por UNIQUE.
--   - cnpj armazenado normalizado (14 digitos), UNIQUE — constraint de unicidade
--     por documento exigida pela Task 16.2. DV ja validado no onboarding PJ de origem.
--   - elegibilidade deriva do resultado KYB/PLD do onboarding (Task 16.4); nasce
--     PENDENTE e a credora so vai a ATIVA apos decisao ELEGIVEL.
--   - FK perfil_credora -> empresa_credora sem ON DELETE CASCADE (preserva trilha
--     LGPD/CMN 4.656 mesmo em delecao indevida).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. empresa_credora
-- -----------------------------------------------------------------------------
CREATE TABLE empresa_credora (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    onboarding_id UUID NOT NULL,
    cnpj VARCHAR(14) NOT NULL,
    razao_social VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    elegibilidade VARCHAR(20) NOT NULL,
    motivo_inelegibilidade VARCHAR(500),
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT uq_empresa_credora_usuario UNIQUE (usuario_id),
    CONSTRAINT uq_empresa_credora_onboarding UNIQUE (onboarding_id),
    CONSTRAINT uq_empresa_credora_cnpj UNIQUE (cnpj),
    CONSTRAINT chk_empresa_credora_status CHECK (status IN ('CADASTRADA', 'ATIVA', 'SUSPENSA')),
    CONSTRAINT chk_empresa_credora_elegibilidade CHECK (elegibilidade IN ('PENDENTE', 'ELEGIVEL', 'INELEGIVEL'))
);

COMMENT ON TABLE empresa_credora IS
    'Agregado raiz da jornada credora (Sprint 16). Vinculo logico com usuario e onboarding via UUID, sem FK fisica.';
COMMENT ON COLUMN empresa_credora.onboarding_id IS
    'Referencia logica a solicitacao_onboarding (tipo EMPRESA) aprovada no modulo onboarding. Sem FK fisica.';
COMMENT ON COLUMN empresa_credora.elegibilidade IS
    'Decisao de elegibilidade derivada do resultado KYB/PLD do onboarding (Task 16.4).';

-- -----------------------------------------------------------------------------
-- 2. perfil_credora
-- -----------------------------------------------------------------------------
CREATE TABLE perfil_credora (
    id UUID PRIMARY KEY,
    empresa_credora_id UUID NOT NULL,
    tipo_credora VARCHAR(30) NOT NULL,
    capacidade_aporte NUMERIC(19, 2),
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT uq_perfil_credora_empresa UNIQUE (empresa_credora_id),
    CONSTRAINT fk_perfil_credora_empresa FOREIGN KEY (empresa_credora_id) REFERENCES empresa_credora (id),
    CONSTRAINT chk_perfil_credora_tipo CHECK (tipo_credora IN ('EMPRESA', 'INSTITUICAO_FINANCEIRA')),
    CONSTRAINT chk_perfil_credora_capacidade CHECK (capacidade_aporte IS NULL OR capacidade_aporte >= 0)
);

COMMENT ON TABLE perfil_credora IS
    'Perfil operacional 1:1 com empresa_credora (Sprint 16). FK sem CASCADE para preservar trilha LGPD/CMN 4.656.';

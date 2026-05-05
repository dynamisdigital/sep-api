-- =============================================================================
-- Migration V1 - Sprint 1 - Esqueleto inicial do schema SEP
-- =============================================================================
-- Cria a tabela `usuario` com campos minimos e auditoria JPA.
-- A Sprint 2 (Gestao de Usuarios) populara o codigo Java + DTOs sobre este
-- schema. A coluna `id` usa o tipo `uuid` nativo do PostgreSQL conforme
-- PRD §16 (Convencoes de Persistencia).
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE usuario (
    id UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(40) NOT NULL,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL DEFAULT 'system',
    modificado_por VARCHAR(50) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_usuario_username UNIQUE (username),
    CONSTRAINT chk_usuario_role CHECK (role IN ('ADMIN', 'CLIENTE'))
);

CREATE INDEX idx_usuario_username ON usuario (username);

COMMENT ON TABLE usuario IS 'Usuario do sistema SEP. Sprint 1 cria o schema; Sprint 2 popula com entidade JPA + DTOs.';
COMMENT ON COLUMN usuario.id IS 'UUID v6 gerado pela aplicacao (PRD §16).';
COMMENT ON COLUMN usuario.username IS 'E-mail unico do usuario (PRD §RF-01).';
COMMENT ON COLUMN usuario.password IS 'Hash BCrypt da senha (Sprint 3).';
COMMENT ON COLUMN usuario.role IS 'Perfil do usuario: ADMIN ou CLIENTE.';

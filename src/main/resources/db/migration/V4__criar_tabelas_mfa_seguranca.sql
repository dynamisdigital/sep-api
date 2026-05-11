-- =============================================================================
-- Migration V4 - Sprint 5 - Endurecimento de Seguranca
-- =============================================================================
-- Cria as 6 tabelas que sustentam MFA TOTP, backup codes, refresh token
-- rotativo, account lockout, step-up authentication e audit log de seguranca.
-- Schema obedece PRD §16 (UUID nativo) e ADR 0010 (MFA TOTP + Biometria).
--
-- Tabelas:
--   - usuario_totp_secret      (1:1 com usuario, secret cifrado em repouso)
--   - usuario_backup_code      (N:1 com usuario, hash BCrypt; uso unico)
--   - refresh_token            (N:1 com usuario, family_id pra reuse detection)
--   - login_attempt            (registra cada tentativa, suporta lockout)
--   - step_up_token            (token efemero para operacoes sensiveis)
--   - audit_log_seguranca      (eventos de seguranca, retencao >= 90 dias)
--
-- Segredos:
--   - TOTP secret nunca em claro (campo `secret_cifrado` com AES por
--     `app.security.totp.encryption-key`).
--   - Backup code nunca em claro (campo `codigo_hash` com BCrypt).
--   - Refresh/step-up token persistidos como `token_hash` (SHA-256 hex).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- TOTP secret (1:1 com usuario)
-- -----------------------------------------------------------------------------
CREATE TABLE usuario_totp_secret (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    secret_cifrado VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    data_ativacao TIMESTAMP WITH TIME ZONE,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL DEFAULT 'system',
    modificado_por VARCHAR(50) NOT NULL DEFAULT 'system',
    CONSTRAINT fk_totp_secret_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT uq_totp_secret_usuario UNIQUE (usuario_id),
    CONSTRAINT chk_totp_secret_status CHECK (status IN ('PENDENTE', 'ATIVO', 'DESABILITADO'))
);

CREATE INDEX idx_totp_secret_usuario ON usuario_totp_secret (usuario_id);

COMMENT ON TABLE usuario_totp_secret IS 'Secret TOTP do usuario (RFC 6238). Secret cifrado em repouso.';
COMMENT ON COLUMN usuario_totp_secret.secret_cifrado IS 'Secret Base32 cifrado com chave por ambiente (app.security.totp.encryption-key).';

-- -----------------------------------------------------------------------------
-- Backup codes (10 por usuario, uso unico)
-- -----------------------------------------------------------------------------
CREATE TABLE usuario_backup_code (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    codigo_hash VARCHAR(255) NOT NULL,
    usado BOOLEAN NOT NULL DEFAULT FALSE,
    usado_em TIMESTAMP WITH TIME ZONE,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL DEFAULT 'system',
    modificado_por VARCHAR(50) NOT NULL DEFAULT 'system',
    CONSTRAINT fk_backup_code_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);

CREATE INDEX idx_backup_code_usuario ON usuario_backup_code (usuario_id);
CREATE INDEX idx_backup_code_usuario_usado ON usuario_backup_code (usuario_id, usado);

COMMENT ON TABLE usuario_backup_code IS 'Backup codes do TOTP (10 codigos, uso unico). Persistencia apenas como hash BCrypt.';

-- -----------------------------------------------------------------------------
-- Refresh token (rotacao + reuse detection)
-- -----------------------------------------------------------------------------
CREATE TABLE refresh_token (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    family_id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    usado_em TIMESTAMP WITH TIME ZONE,
    revogado_em TIMESTAMP WITH TIME ZONE,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL DEFAULT 'system',
    modificado_por VARCHAR(50) NOT NULL DEFAULT 'system',
    CONSTRAINT fk_refresh_token_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_refresh_token_status CHECK (status IN ('ATIVO', 'USADO', 'REVOGADO', 'EXPIRADO'))
);

CREATE INDEX idx_refresh_token_usuario ON refresh_token (usuario_id);
CREATE INDEX idx_refresh_token_family ON refresh_token (family_id);
CREATE INDEX idx_refresh_token_expira ON refresh_token (expira_em);

COMMENT ON TABLE refresh_token IS 'Refresh token rotativo (Sprint 5). family_id mantem cadeia da sessao para reuse detection.';
COMMENT ON COLUMN refresh_token.token_hash IS 'SHA-256 hex do token; token cru e devolvido uma unica vez ao cliente.';

-- -----------------------------------------------------------------------------
-- Login attempt (suporta lockout)
-- -----------------------------------------------------------------------------
CREATE TABLE login_attempt (
    id UUID PRIMARY KEY,
    usuario_id UUID,
    username VARCHAR(255) NOT NULL,
    ip VARCHAR(45),
    user_agent VARCHAR(500),
    status VARCHAR(40) NOT NULL,
    data_tentativa TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_login_attempt_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT chk_login_attempt_status CHECK (status IN (
        'SUCESSO', 'SENHA_INVALIDA', 'USUARIO_INEXISTENTE',
        'CONTA_BLOQUEADA', 'TOTP_INVALIDO', 'TOTP_NECESSARIO'
    ))
);

CREATE INDEX idx_login_attempt_username_data ON login_attempt (username, data_tentativa DESC);
CREATE INDEX idx_login_attempt_ip_data ON login_attempt (ip, data_tentativa DESC);
CREATE INDEX idx_login_attempt_usuario_data ON login_attempt (usuario_id, data_tentativa DESC);

COMMENT ON TABLE login_attempt IS 'Tentativas de login para suportar lockout e auditoria.';

-- -----------------------------------------------------------------------------
-- Step-up token (operacoes sensiveis)
-- -----------------------------------------------------------------------------
CREATE TABLE step_up_token (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    usado BOOLEAN NOT NULL DEFAULT FALSE,
    usado_em TIMESTAMP WITH TIME ZONE,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL DEFAULT 'system',
    modificado_por VARCHAR(50) NOT NULL DEFAULT 'system',
    CONSTRAINT fk_step_up_token_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT uq_step_up_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_step_up_token_usuario ON step_up_token (usuario_id);
CREATE INDEX idx_step_up_token_expira ON step_up_token (expira_em);

COMMENT ON TABLE step_up_token IS 'Token efemero (5 min) que confirma desafio MFA para operacoes sensiveis.';

-- -----------------------------------------------------------------------------
-- Audit log de seguranca (retencao >= 90 dias - LGPD Art. 16)
-- -----------------------------------------------------------------------------
CREATE TABLE audit_log_seguranca (
    id UUID PRIMARY KEY,
    tipo VARCHAR(50) NOT NULL,
    usuario_id UUID,
    ip VARCHAR(45),
    user_agent VARCHAR(500),
    detalhes JSONB,
    data_evento TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_audit_seguranca_tipo CHECK (tipo IN (
        'LOGIN_OK', 'LOGIN_FAIL',
        'TOTP_OK', 'TOTP_FAIL',
        'BACKUP_CODE_USED',
        'LOCKOUT',
        'PASSWORD_CHANGED',
        'MFA_ENABLED', 'MFA_DISABLED',
        'REFRESH_REUSE_DETECTED',
        'STEP_UP_OK', 'STEP_UP_FAIL'
    ))
);

CREATE INDEX idx_audit_seguranca_usuario_data ON audit_log_seguranca (usuario_id, data_evento DESC);
CREATE INDEX idx_audit_seguranca_tipo_data ON audit_log_seguranca (tipo, data_evento DESC);

COMMENT ON TABLE audit_log_seguranca IS 'Eventos de seguranca (separado da auditoria JPA generica). Retencao >= 90 dias.';
COMMENT ON COLUMN audit_log_seguranca.detalhes IS 'Detalhes do evento em JSON (sem dados sensiveis em claro).';

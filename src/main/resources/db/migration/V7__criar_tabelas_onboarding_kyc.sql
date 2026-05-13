-- =============================================================================
-- V7 — Onboarding KYC Pessoa Fisica (Sprint 6)
-- =============================================================================
-- Modulo `onboarding` para verificacao de identidade (KYC PF) conforme
-- Resolucao CMN 4.656/2018 Art. 8.
--
-- Tabelas criadas:
--   - solicitacao_onboarding   (agregado raiz)
--   - documento_cadastral      (1:N — documentos anexados)
--   - resultado_verificacao    (1:1 — resultado do KycProvider)
--
-- Tambem atualiza chk_audit_seguranca_tipo para incluir os 6 novos eventos
-- KYC_* da Task 6.1 / 6.6.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Solicitacao de onboarding (agregado raiz)
-- -----------------------------------------------------------------------------
CREATE TABLE solicitacao_onboarding (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    cpf VARCHAR(11) NOT NULL,
    nome_completo VARCHAR(255) NOT NULL,
    data_nascimento DATE NOT NULL,
    status VARCHAR(40) NOT NULL,
    id_verificacao_externa VARCHAR(120),
    revisao_documentos INTEGER NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT fk_solicitacao_onboarding_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT chk_solicitacao_onboarding_status CHECK (status IN (
        'INICIADO', 'DOCUMENTOS_RECEBIDOS', 'EM_VERIFICACAO',
        'APROVADO', 'REPROVADO', 'PENDENCIA'
    ))
);

CREATE INDEX idx_onboarding_usuario_data
    ON solicitacao_onboarding (usuario_id, data_criacao DESC);

CREATE INDEX idx_onboarding_cpf_status
    ON solicitacao_onboarding (cpf, status);

CREATE INDEX idx_onboarding_verificacao_externa
    ON solicitacao_onboarding (id_verificacao_externa);

-- Indice unico parcial: impede CPF duplicado em qualquer status ativo (REPROVADO
-- libera o CPF para nova tentativa).
CREATE UNIQUE INDEX uq_onboarding_cpf_ativo
    ON solicitacao_onboarding (cpf)
    WHERE status IN ('INICIADO', 'DOCUMENTOS_RECEBIDOS', 'EM_VERIFICACAO', 'APROVADO', 'PENDENCIA');

COMMENT ON TABLE solicitacao_onboarding IS 'Solicitacao KYC PF (CMN 4.656/2018 Art. 8). Agregado raiz do modulo onboarding.';
COMMENT ON COLUMN solicitacao_onboarding.id_verificacao_externa IS 'ID retornado pelo KycProvider (Celcoin) para correlacionar webhook.';
COMMENT ON COLUMN solicitacao_onboarding.revisao_documentos IS 'Contador de uploads — base da Idempotency-Key deterministica do disparo da verificacao.';

-- -----------------------------------------------------------------------------
-- 2. Documentos cadastrais anexados
-- -----------------------------------------------------------------------------
CREATE TABLE documento_cadastral (
    id UUID PRIMARY KEY,
    solicitacao_id UUID NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    conteudo BYTEA NOT NULL,
    mime_type VARCHAR(50) NOT NULL,
    nome_original VARCHAR(255) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    data_envio TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_documento_cadastral_solicitacao
        FOREIGN KEY (solicitacao_id) REFERENCES solicitacao_onboarding (id) ON DELETE CASCADE,
    CONSTRAINT chk_documento_cadastral_tipo CHECK (tipo IN ('RG', 'CNH', 'PASSAPORTE', 'SELFIE')),
    CONSTRAINT chk_documento_cadastral_mime CHECK (mime_type IN ('image/jpeg', 'image/png', 'application/pdf')),
    CONSTRAINT chk_documento_cadastral_tamanho CHECK (tamanho_bytes > 0 AND tamanho_bytes <= 10485760)
);

CREATE INDEX idx_documento_cadastral_solicitacao
    ON documento_cadastral (solicitacao_id);

COMMENT ON TABLE documento_cadastral IS 'Documentos anexados a uma solicitacao de onboarding (storage BYTEA temporario; sera migrado para S3/MinIO em Epic 16).';
COMMENT ON COLUMN documento_cadastral.sha256 IS 'Hash hex SHA-256 do conteudo — usado em logs/audit em vez do binario (LGPD).';

-- -----------------------------------------------------------------------------
-- 3. Resultado da verificacao (1:1 com solicitacao)
-- -----------------------------------------------------------------------------
CREATE TABLE resultado_verificacao (
    id UUID PRIMARY KEY,
    solicitacao_id UUID NOT NULL UNIQUE,
    status_final VARCHAR(40) NOT NULL,
    motivo TEXT,
    payload_provider JSONB,
    data_resultado TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_resultado_verificacao_solicitacao
        FOREIGN KEY (solicitacao_id) REFERENCES solicitacao_onboarding (id) ON DELETE CASCADE,
    CONSTRAINT chk_resultado_status_final CHECK (status_final IN ('APROVADO', 'REPROVADO', 'PENDENCIA'))
);

COMMENT ON TABLE resultado_verificacao IS 'Resultado final do KycProvider (Celcoin). Guarda payload bruto JSONB para trilha auditavel regulatoria.';
COMMENT ON COLUMN resultado_verificacao.payload_provider IS 'Payload cru do provider. NUNCA replicar em audit_log_seguranca.';

-- -----------------------------------------------------------------------------
-- 4. Atualizar chk_audit_seguranca_tipo para incluir eventos KYC_*
-- -----------------------------------------------------------------------------
ALTER TABLE audit_log_seguranca DROP CONSTRAINT chk_audit_seguranca_tipo;

ALTER TABLE audit_log_seguranca ADD CONSTRAINT chk_audit_seguranca_tipo CHECK (tipo IN (
    'LOGIN_OK', 'LOGIN_FAIL',
    'TOTP_OK', 'TOTP_FAIL',
    'BACKUP_CODE_USED',
    'LOCKOUT',
    'PASSWORD_CHANGED',
    'MFA_ENABLED', 'MFA_DISABLED',
    'REFRESH_REUSE_DETECTED',
    'STEP_UP_OK', 'STEP_UP_FAIL',
    'KYC_INICIADO',
    'KYC_DOCUMENTO_ENVIADO',
    'KYC_VERIFICACAO_DISPARADA',
    'KYC_FINALIZADO_APROVADO',
    'KYC_FINALIZADO_REPROVADO',
    'KYC_FINALIZADO_PENDENCIA'
));

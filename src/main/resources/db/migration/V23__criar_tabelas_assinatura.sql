-- =============================================================================
-- V23 — Sprint 11 Task 11.2: tabelas do ciclo de assinatura digital + CCB.
-- =============================================================================
-- Modulo `contratos` ganha 3 entidades:
--   envelope_assinatura  — vincula versao do contrato a um provider externo
--   evento_assinatura    — historico de callbacks recebidos (idempotente)
--   documento_assinado   — PDF assinado retornado pelo provider (1:1 envelope)
--
-- Adiciona estado RECUSADO ao check de contrato.status.
--
-- Retencao minima de 10 anos (CMN 4.656/2018 Art. 11 + Lei 10.931/2004 + LGPD).
-- FKs sem CASCADE para preservar trilha legal em caso de remocao indevida.
-- =============================================================================

-- 1. Atualiza check de status do contrato para incluir RECUSADO ---------------

ALTER TABLE contrato DROP CONSTRAINT chk_contrato_status;
ALTER TABLE contrato ADD CONSTRAINT chk_contrato_status CHECK (
    status IN ('GERADO', 'AGUARDANDO_ACEITE', 'ACEITO', 'EM_ASSINATURA', 'ASSINADO', 'RECUSADO', 'CANCELADO')
);

-- 2. envelope_assinatura ------------------------------------------------------

CREATE TABLE envelope_assinatura (
    id UUID PRIMARY KEY,
    contrato_id UUID NOT NULL REFERENCES contrato(id),
    versao_id UUID NOT NULL UNIQUE REFERENCES versao_contrato(id),
    provider VARCHAR(40) NOT NULL,
    id_envelope_externo VARCHAR(100),
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(40) NOT NULL,
    hash_pdf_enviado VARCHAR(64) NOT NULL,
    data_envio TIMESTAMP WITH TIME ZONE,
    data_atualizacao_provider TIMESTAMP WITH TIME ZONE,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT chk_envelope_status CHECK (
        status IN ('RASCUNHO', 'ENVIADO', 'VISUALIZADO', 'ASSINADO', 'RECUSADO', 'EXPIRADO')
    ),
    CONSTRAINT chk_envelope_hash_pdf_hex CHECK (hash_pdf_enviado ~ '^[a-f0-9]{64}$'),
    CONSTRAINT uq_envelope_provider_id_externo UNIQUE (provider, id_envelope_externo)
);

CREATE INDEX idx_envelope_assinatura_contrato_id ON envelope_assinatura(contrato_id);
CREATE INDEX idx_envelope_assinatura_status ON envelope_assinatura(status);

-- 3. evento_assinatura --------------------------------------------------------

CREATE TABLE evento_assinatura (
    id UUID PRIMARY KEY,
    envelope_id UUID NOT NULL REFERENCES envelope_assinatura(id),
    id_evento_externo VARCHAR(100) NOT NULL,
    status VARCHAR(40) NOT NULL,
    payload_resumo VARCHAR(1000),
    data_evento TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_evento_assinatura_envelope_externo UNIQUE (envelope_id, id_evento_externo),
    CONSTRAINT chk_evento_status CHECK (
        status IN ('RASCUNHO', 'ENVIADO', 'VISUALIZADO', 'ASSINADO', 'RECUSADO', 'EXPIRADO')
    )
);

CREATE INDEX idx_evento_assinatura_envelope_id ON evento_assinatura(envelope_id);

-- 4. documento_assinado -------------------------------------------------------

CREATE TABLE documento_assinado (
    id UUID PRIMARY KEY,
    envelope_id UUID NOT NULL UNIQUE REFERENCES envelope_assinatura(id),
    hash_sha256 VARCHAR(64) NOT NULL,
    data_assinatura TIMESTAMP WITH TIME ZONE NOT NULL,
    selo VARCHAR(500),
    conteudo BYTEA NOT NULL,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT chk_doc_assinado_hash_hex CHECK (hash_sha256 ~ '^[a-f0-9]{64}$'),
    CONSTRAINT chk_doc_assinado_conteudo_nao_vazio CHECK (octet_length(conteudo) > 0)
);

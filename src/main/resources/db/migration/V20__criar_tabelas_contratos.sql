-- Sprint 10 Task 10.1: tabelas do modulo contratos (formalizacao contratual).
--
-- Contratos sao documentos legais. Retencao minima: 10 anos (Resolucao CMN 4.656/2018 + LGPD).
-- FKs sem CASCADE para preservar historico em caso de remocao indevida de proposta/usuario.

CREATE TABLE contrato (
    id UUID PRIMARY KEY,
    proposta_id UUID NOT NULL UNIQUE REFERENCES proposta_credito(id),
    tomador_id UUID NOT NULL REFERENCES usuario(id),
    tipo VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT chk_contrato_tipo CHECK (tipo IN ('MUTUO', 'CCB', 'OUTROS')),
    CONSTRAINT chk_contrato_status CHECK (
        status IN ('GERADO', 'AGUARDANDO_ACEITE', 'ACEITO', 'EM_ASSINATURA', 'ASSINADO', 'CANCELADO')
    )
);

CREATE INDEX idx_contrato_proposta_id ON contrato(proposta_id);
CREATE INDEX idx_contrato_tomador_id ON contrato(tomador_id);
CREATE INDEX idx_contrato_status ON contrato(status);

CREATE TABLE versao_contrato (
    id UUID PRIMARY KEY,
    contrato_id UUID NOT NULL REFERENCES contrato(id),
    numero INTEGER NOT NULL,
    conteudo_texto TEXT NOT NULL,
    hash_sha256 VARCHAR(64) NOT NULL,
    data_geracao TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_versao_contrato_numero UNIQUE (contrato_id, numero),
    CONSTRAINT chk_versao_hash_hex CHECK (hash_sha256 ~ '^[a-f0-9]{64}$')
);

CREATE INDEX idx_versao_contrato_contrato_id ON versao_contrato(contrato_id);

CREATE TABLE clausula_contratual (
    id UUID PRIMARY KEY,
    versao_id UUID NOT NULL REFERENCES versao_contrato(id),
    ordem INTEGER NOT NULL,
    titulo VARCHAR(255) NOT NULL,
    texto TEXT NOT NULL,
    CONSTRAINT uq_clausula_versao_ordem UNIQUE (versao_id, ordem),
    CONSTRAINT chk_clausula_ordem_positiva CHECK (ordem > 0)
);

CREATE INDEX idx_clausula_versao_id ON clausula_contratual(versao_id);

CREATE TABLE aceite_contrato (
    id UUID PRIMARY KEY,
    versao_id UUID NOT NULL UNIQUE REFERENCES versao_contrato(id),
    tomador_id UUID NOT NULL REFERENCES usuario(id),
    data_aceite TIMESTAMP WITH TIME ZONE NOT NULL,
    ip_origem VARCHAR(45),
    user_agent_origem VARCHAR(500)
);

CREATE INDEX idx_aceite_contrato_tomador_id ON aceite_contrato(tomador_id);

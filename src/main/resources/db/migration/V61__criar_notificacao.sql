-- =============================================================================
-- V61 — Sprint 38 Task 38.2: historico de notificacoes por usuario (ADR 0021)
-- =============================================================================
-- Cria notificacao: modulo transversal com canal IN_APP (central) e historico
-- de EMAIL. A regua de cobranca segue em evento_cobranca (V30), sem mudanca.
--
-- Decisoes:
--   - Destinatario unico: usuario_id NOT NULL, FK para usuario SEM ON DELETE
--     CASCADE (trilha auditavel). Grupo/broadcast vira N linhas, nao esquema.
--   - Idempotencia por origem: UNIQUE parcial (tipo, origem_id, usuario_id)
--     WHERE situacao <> 'FALHOU'. Tentativa que falhou nao bloqueia a proxima.
--   - Minimizacao (LGPD): nao existe coluna de e-mail, CPF/CNPJ, chave Pix,
--     valor ou payload do evento. Titulo/mensagem sao texto fixo por tipo;
--     unica referencia e o par referencia_tipo/referencia_id por allowlist.
--   - Leitura separada da entrega: lida_em so existe para IN_APP.
--   - Retencao provisoria de 5 anos a partir de criada_em, por procedimento
--     manual (NOTIFICACOES.md); nenhum expurgo automatico.
--   - Os CHECKs repetem as invariantes do agregado Notificacao/Entrega.
-- =============================================================================

CREATE TABLE notificacao (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    origem_id VARCHAR(100) NOT NULL,
    canal VARCHAR(20) NOT NULL,
    situacao VARCHAR(20) NOT NULL,
    situacao_atualizada_em TIMESTAMP WITH TIME ZONE NOT NULL,
    motivo_falha VARCHAR(200),
    titulo VARCHAR(120) NOT NULL,
    mensagem VARCHAR(500) NOT NULL,
    referencia_tipo VARCHAR(20),
    referencia_id UUID,
    criada_em TIMESTAMP WITH TIME ZONE NOT NULL,
    lida_em TIMESTAMP WITH TIME ZONE,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_modificacao TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_por VARCHAR(50) NOT NULL,
    modificado_por VARCHAR(50) NOT NULL,
    CONSTRAINT fk_notificacao_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT chk_notificacao_tipo CHECK (tipo IN ('DESEMBOLSO_PIX_CONCLUIDO', 'CONTA_BLOQUEADA')),
    CONSTRAINT chk_notificacao_canal_situacao CHECK (
        (canal = 'IN_APP' AND situacao = 'DISPONIVEL')
        OR (canal = 'EMAIL' AND situacao IN ('PENDENTE', 'ENVIADA', 'SIMULADA', 'FALHOU'))
    ),
    CONSTRAINT chk_notificacao_motivo_falha CHECK ((situacao = 'FALHOU') = (motivo_falha IS NOT NULL)),
    CONSTRAINT chk_notificacao_leitura_in_app CHECK (lida_em IS NULL OR canal = 'IN_APP'),
    CONSTRAINT chk_notificacao_referencia CHECK (
        (referencia_tipo IS NULL AND referencia_id IS NULL)
        OR (referencia_tipo = 'CONTRATO' AND referencia_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_notificacao_origem
    ON notificacao (tipo, origem_id, usuario_id)
    WHERE situacao <> 'FALHOU';

CREATE INDEX idx_notificacao_central
    ON notificacao (usuario_id, criada_em DESC, id DESC)
    WHERE canal = 'IN_APP';

CREATE INDEX idx_notificacao_nao_lidas
    ON notificacao (usuario_id)
    WHERE canal = 'IN_APP' AND lida_em IS NULL;

COMMENT ON TABLE notificacao IS 'Historico de notificacoes por usuario (Sprint 38, ADR 0021). IN_APP alimenta a central; EMAIL e trilha de envio. Sem dado pessoal alem do usuario_id. Retencao provisoria de 5 anos a partir de criada_em.';
COMMENT ON COLUMN notificacao.origem_id IS 'Identificador derivado do fato de origem (id da transferencia, instante do bloqueio); nunca aleatorio. Chave de idempotencia com tipo e usuario_id.';
COMMENT ON COLUMN notificacao.situacao IS 'IN_APP: DISPONIVEL. EMAIL: PENDENTE -> ENVIADA (provider aceitou) | SIMULADA (adapter de log) | FALHOU.';
COMMENT ON COLUMN notificacao.motivo_falha IS 'Motivo sanitizado (nome da classe da excecao), nunca a mensagem da excecao.';
COMMENT ON COLUMN notificacao.lida_em IS 'Instante da primeira leitura; so para IN_APP. Remarcar preserva o valor.';

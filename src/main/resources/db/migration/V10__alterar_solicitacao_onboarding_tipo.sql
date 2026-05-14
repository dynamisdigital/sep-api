-- =============================================================================
-- V10 — Onboarding PF/PJ + status PLD (Sprint 7 — Task 7.1)
-- =============================================================================
-- Generaliza `solicitacao_onboarding` pra suportar tanto KYC PF (Sprint 6)
-- quanto KYB PJ (Sprint 7), de forma aditiva e sem perda de dados.
--
-- Alteracoes:
--   1. Coluna `tipo` (PESSOA|EMPRESA) — backfill PESSOA pras linhas PF da Sprint 6.
--   2. Coluna `documento` (14 char normalizada — CPF 11 ou CNPJ 14) — backfill = cpf.
--   3. `cpf` e `data_nascimento` viram nullable (PJ nao tem CPF/nascimento).
--   4. Indice unico ativo trocado: cpf -> documento, com novos status finais.
--   5. Status check ampliado: APROVADO_FINAL (pos-PLD limpo) e REPROVADO_PLD (hit PLD).
-- =============================================================================

-- 1. Adicionar tipo e fazer backfill PESSOA
ALTER TABLE solicitacao_onboarding ADD COLUMN tipo VARCHAR(20);
UPDATE solicitacao_onboarding SET tipo = 'PESSOA' WHERE tipo IS NULL;
ALTER TABLE solicitacao_onboarding ALTER COLUMN tipo SET NOT NULL;
ALTER TABLE solicitacao_onboarding ADD CONSTRAINT chk_solicitacao_onboarding_tipo
    CHECK (tipo IN ('PESSOA', 'EMPRESA'));

-- 2. Adicionar documento canonico e fazer backfill a partir de cpf
ALTER TABLE solicitacao_onboarding ADD COLUMN documento VARCHAR(14);
UPDATE solicitacao_onboarding SET documento = cpf WHERE documento IS NULL;
ALTER TABLE solicitacao_onboarding ALTER COLUMN documento SET NOT NULL;

-- 3. Relaxar NOT NULL de cpf e data_nascimento (PJ nao tem)
ALTER TABLE solicitacao_onboarding ALTER COLUMN cpf DROP NOT NULL;
ALTER TABLE solicitacao_onboarding ALTER COLUMN data_nascimento DROP NOT NULL;

-- 4. Trocar indice unico ativo de cpf -> documento (ja considerando novos status)
DROP INDEX IF EXISTS uq_onboarding_cpf_ativo;
CREATE UNIQUE INDEX uq_onboarding_documento_ativo
    ON solicitacao_onboarding (documento)
    WHERE status IN (
        'INICIADO', 'DOCUMENTOS_RECEBIDOS', 'EM_VERIFICACAO',
        'APROVADO', 'PENDENCIA', 'APROVADO_FINAL'
    );

-- Indice de consulta por documento + status (substitui idx_onboarding_cpf_status)
DROP INDEX IF EXISTS idx_onboarding_cpf_status;
CREATE INDEX idx_onboarding_documento_status
    ON solicitacao_onboarding (documento, status);

-- 5. Ampliar status check com APROVADO_FINAL e REPROVADO_PLD
ALTER TABLE solicitacao_onboarding DROP CONSTRAINT chk_solicitacao_onboarding_status;
ALTER TABLE solicitacao_onboarding ADD CONSTRAINT chk_solicitacao_onboarding_status CHECK (status IN (
    'INICIADO', 'DOCUMENTOS_RECEBIDOS', 'EM_VERIFICACAO',
    'APROVADO', 'REPROVADO', 'PENDENCIA',
    'APROVADO_FINAL', 'REPROVADO_PLD'
));

COMMENT ON COLUMN solicitacao_onboarding.tipo IS 'Discriminador PF/PJ — PESSOA (KYC) ou EMPRESA (KYB).';
COMMENT ON COLUMN solicitacao_onboarding.documento IS 'Documento canonico normalizado — CPF (11) ou CNPJ (14). Substitui cpf no fluxo Sprint 7+.';
COMMENT ON COLUMN solicitacao_onboarding.cpf IS 'Mantido por compatibilidade pra solicitacoes PF; null para EMPRESA.';

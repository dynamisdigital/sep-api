-- Sprint 15 — Hardening (15F-026)
-- Adiciona coluna `versao` em `solicitacao_onboarding` pra optimistic locking via @Version.
-- Webhooks KYC/KYB/PLD chegando concorrentes em ordem invertida podem causar lost update
-- no `finalizar()` ja que nao havia controle de versao. JPA `@Version` rejeita commit
-- quando outra transacao alterou o registro entre o load e o flush.
--
-- Valor inicial 0 pra registros existentes. Hibernate incrementa em cada update.

ALTER TABLE solicitacao_onboarding
    ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN solicitacao_onboarding.versao IS
    'Optimistic locking via JPA @Version (Sprint 15 — 15F-026). Rejeita lost update '
    'em callbacks concorrentes KYC/KYB/PLD.';

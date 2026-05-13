-- =============================================================================
-- V9 — Reverte CASCADE em fk_solicitacao_onboarding_usuario
-- =============================================================================
-- A V8 adicionou ON DELETE CASCADE como atalho para isolamento de testes legacy
-- de UsuarioRepository.deleteAll(). Em revisao, isso foi considerado risco
-- LGPD/regulatorio: KYC e trilha auditavel exigida pela Resolucao CMN
-- 4.656/2018; cascata em delecao fisica de usuario apagaria documentos
-- cadastrais e resultados de verificacao silenciosamente.
--
-- Producao nao deleta usuario fisicamente (PRD §16: "soft delete nao sera
-- adotado nesta fase"), entao a constraint volta ao default NO ACTION; o
-- isolamento dos testes E2E e resolvido via @AfterEach explicito limpando
-- as tabelas filhas em ordem, sem mudar semantica produtiva.
-- =============================================================================

ALTER TABLE solicitacao_onboarding
    DROP CONSTRAINT fk_solicitacao_onboarding_usuario;

ALTER TABLE solicitacao_onboarding
    ADD CONSTRAINT fk_solicitacao_onboarding_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id);

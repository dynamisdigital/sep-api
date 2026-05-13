-- =============================================================================
-- V8 — ON DELETE CASCADE em fk_solicitacao_onboarding_usuario
-- =============================================================================
-- A FK criada em V7 sem ON DELETE CASCADE quebrou testes legacy de
-- UsuarioRepository que limpam a tabela usuario via deleteAll() entre cenarios
-- (e.g. @DataJpaTest com Flyway). Em producao a delecao fisica de usuario nao
-- ocorre (PRD §16 — "soft delete nao sera adotado nesta fase"), mas o teste
-- precisa do CASCADE para isolamento de cenarios.
--
-- Mesma estrategia da V5 (cascade_fks_mfa_seguranca) para outras tabelas
-- filhas de usuario.
-- =============================================================================

ALTER TABLE solicitacao_onboarding
    DROP CONSTRAINT fk_solicitacao_onboarding_usuario;

ALTER TABLE solicitacao_onboarding
    ADD CONSTRAINT fk_solicitacao_onboarding_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE;

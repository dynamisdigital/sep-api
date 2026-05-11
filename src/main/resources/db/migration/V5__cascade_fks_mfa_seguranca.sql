-- =============================================================================
-- Migration V5 - Sprint 5 Task 5.3 - Cascade nas FKs MFA
-- =============================================================================
-- V4 criou FKs sem ON DELETE, o que impedia exclusao de usuario quando
-- tinha refresh_token / totp_secret / backup_code / step_up_token.
--
-- Politica:
--   - usuario_totp_secret, usuario_backup_code, refresh_token,
--     step_up_token => ON DELETE CASCADE (dados de sessao do usuario,
--     descartaveis ao apagar usuario)
--   - login_attempt, audit_log_seguranca => ON DELETE SET NULL (preserva
--     historico para auditoria; usuario_id vira NULL)
-- =============================================================================

ALTER TABLE usuario_totp_secret DROP CONSTRAINT fk_totp_secret_usuario;
ALTER TABLE usuario_totp_secret
    ADD CONSTRAINT fk_totp_secret_usuario FOREIGN KEY (usuario_id)
    REFERENCES usuario (id) ON DELETE CASCADE;

ALTER TABLE usuario_backup_code DROP CONSTRAINT fk_backup_code_usuario;
ALTER TABLE usuario_backup_code
    ADD CONSTRAINT fk_backup_code_usuario FOREIGN KEY (usuario_id)
    REFERENCES usuario (id) ON DELETE CASCADE;

ALTER TABLE refresh_token DROP CONSTRAINT fk_refresh_token_usuario;
ALTER TABLE refresh_token
    ADD CONSTRAINT fk_refresh_token_usuario FOREIGN KEY (usuario_id)
    REFERENCES usuario (id) ON DELETE CASCADE;

ALTER TABLE step_up_token DROP CONSTRAINT fk_step_up_token_usuario;
ALTER TABLE step_up_token
    ADD CONSTRAINT fk_step_up_token_usuario FOREIGN KEY (usuario_id)
    REFERENCES usuario (id) ON DELETE CASCADE;

ALTER TABLE login_attempt DROP CONSTRAINT fk_login_attempt_usuario;
ALTER TABLE login_attempt
    ADD CONSTRAINT fk_login_attempt_usuario FOREIGN KEY (usuario_id)
    REFERENCES usuario (id) ON DELETE SET NULL;

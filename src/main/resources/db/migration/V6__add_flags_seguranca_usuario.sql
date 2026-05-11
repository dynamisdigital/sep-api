-- =============================================================================
-- Migration V6 - Sprint 5 Task 5.5 - Flags de seguranca no usuario
-- =============================================================================
-- Adiciona:
--   - precisa_redefinir_senha BOOLEAN: forca usuario a redefinir senha no
--     proximo login. Inicializado TRUE para todos os usuarios existentes
--     (que ainda tem senha de 6 chars; nova politica = 12+ chars OU passphrase).
--   - mfa_habilitado BOOLEAN: cache rapido do estado do MFA TOTP (evita join
--     em login). Mantido em sync por HabilitarTotp/Confirmar/Desabilitar
--     (Sprint 5 Task 5.2/5.5).
-- =============================================================================

ALTER TABLE usuario
    ADD COLUMN precisa_redefinir_senha BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN mfa_habilitado BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE usuario SET precisa_redefinir_senha = TRUE;

COMMENT ON COLUMN usuario.precisa_redefinir_senha IS 'Forca redefinicao no proximo login (Sprint 5 - nova politica de senha).';
COMMENT ON COLUMN usuario.mfa_habilitado IS 'Cache do estado ATIVO do TOTP. Verdade autoritativa fica em usuario_totp_secret.';

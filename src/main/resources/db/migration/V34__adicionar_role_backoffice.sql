-- =============================================================================
-- V34 — Sprint 14 Task 14.6: nova role BACKOFFICE
-- =============================================================================
-- Adiciona a role 'BACKOFFICE' (operador interno de backoffice operacional) ao
-- check constraint de usuario.role. Preserva dados existentes (so altera o
-- conjunto de valores aceitos).
--
-- BACKOFFICE eh cumulativa com FINANCEIRO — usuario pode acumular as duas.
-- Permite operar a fila operacional (Sprint 14) e reprocessos manuais sem
-- conceder permissoes amplas de FINANCEIRO (parecer de credito, registrar
-- recebimento ficam restritos a FINANCEIRO + ADMIN).
-- =============================================================================

ALTER TABLE usuario DROP CONSTRAINT chk_usuario_role;

ALTER TABLE usuario ADD CONSTRAINT chk_usuario_role
    CHECK (role IN ('ADMIN', 'CLIENTE', 'FINANCEIRO', 'BACKOFFICE'));

COMMENT ON COLUMN usuario.role IS
    'Perfil do usuario: ADMIN (interno full), CLIENTE (tomador/credor), FINANCEIRO (parecer de credito Sprint 8), BACKOFFICE (operacao da fila/reprocessos Sprint 14).';

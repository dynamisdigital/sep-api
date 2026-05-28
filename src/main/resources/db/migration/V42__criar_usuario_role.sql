-- =============================================================================
-- V42 — Sprint 18 Task 18.2: roles cumulativas por usuario (Epic 11)
-- =============================================================================
-- Cria a tabela de associacao usuario_role (fonte autoritativa do conjunto de
-- roles) e faz backfill da role atual de cada usuario, sem perda de acesso.
--
-- Decisoes:
--   - PK (usuario_id, role) impede role duplicada para o mesmo usuario.
--   - FK -> usuario(id) sem ON DELETE CASCADE (preserva trilha; usuario nao e
--     deletado fisicamente nesta fase).
--   - A coluna usuario.role e MANTIDA como role principal denormalizada (maior
--     precedencia), sincronizada pelo dominio, para compatibilidade com claims
--     e consumidores legados ate a migracao completa para multi-role (Task 18.3).
--   - CHECK alinhado ao enum Role.
-- =============================================================================

CREATE TABLE usuario_role (
    usuario_id UUID NOT NULL,
    role VARCHAR(40) NOT NULL,
    CONSTRAINT pk_usuario_role PRIMARY KEY (usuario_id, role),
    CONSTRAINT fk_usuario_role_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT chk_usuario_role CHECK (role IN ('ADMIN', 'CLIENTE', 'FINANCEIRO', 'BACKOFFICE'))
);

CREATE INDEX idx_usuario_role_usuario ON usuario_role (usuario_id);

-- Backfill: cada usuario passa a ter, no minimo, a role atual.
INSERT INTO usuario_role (usuario_id, role)
SELECT id, role FROM usuario;

COMMENT ON TABLE usuario_role IS
    'Roles cumulativas por usuario (Sprint 18). Fonte autoritativa; usuario.role e a principal denormalizada sincronizada pelo dominio.';

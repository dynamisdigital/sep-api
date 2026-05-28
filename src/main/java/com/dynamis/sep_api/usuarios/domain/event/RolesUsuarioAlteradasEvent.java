package com.dynamis.sep_api.usuarios.domain.event;

import com.dynamis.sep_api.usuarios.domain.model.Role;

import java.util.Set;
import java.util.UUID;

/**
 * Evento publicado quando o conjunto cumulativo de roles de um usuario e alterado (Sprint 18 Task
 * 18.5). Carrega o conjunto anterior e o novo para auditoria (Task 18.6 — {@code
 * USUARIO_ROLES_ALTERADAS}).
 */
public record RolesUsuarioAlteradasEvent(
        UUID atorAdminId, UUID usuarioAlvoId, Set<Role> rolesAnteriores, Set<Role> rolesNovas) {}

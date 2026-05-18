package com.dynamis.sep_api.usuarios.domain.event;

import com.dynamis.sep_api.usuarios.domain.model.Role;

import java.util.UUID;

/**
 * Evento de dominio: ADMIN alterou a role de um usuario (Sprint 8 Task 8.4). Consumido pelo
 * {@code UsuariosAuditListener} (Task 8.6) para gravar {@code ROLE_ALTERADO} em
 * {@code audit_log_seguranca} via padrao AFTER_COMMIT + REQUIRES_NEW — consistente com os demais
 * listeners de auditoria reforcada (KYC/KYB/PLD/credito).
 */
public record RoleAlteradaEvent(UUID atorAdminId, UUID usuarioAlvoId, Role roleAnterior, Role roleNova) {}

package com.dynamis.sep_api.credores.domain.event;

import java.util.UUID;

/**
 * Evento publicado quando uma empresa credora e cadastrada com sucesso (Sprint 16). Consumido pelo
 * listener de auditoria; {@code cnpj} e mascarado antes de entrar no audit log (LGPD/CMN 4.656).
 */
public record EmpresaCredoraCadastradaEvent(UUID empresaCredoraId, UUID usuarioId, String cnpj) {}

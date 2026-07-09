package com.dynamis.sep_api.credores.domain.event;

import java.util.UUID;

/**
 * Aporte da credora com falha terminal na reconciliacao (Sprint 29 Task 29.5). Publicado somente na
 * primeira falha — replay idempotente nao republica. {@code motivoSanitizado} nunca carrega erro
 * bruto de provider.
 */
public record AporteCredoraFalhouEvent(
        UUID aporteId, UUID operacaoId, UUID empresaCredoraId, String motivoSanitizado) {}

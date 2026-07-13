package com.dynamis.sep_api.credores.domain.event;

import java.util.UUID;

/**
 * Sugestao de matching rejeitada por decisao assistida (Sprint 30). {@code motivoSanitizado} ja
 * chega tratado pelo dominio (nunca texto bruto). O par nao volta a ser sugerido (Task 30.1).
 */
public record MatchingCredoraRejeitadoEvent(
        UUID matchingId, UUID operacaoId, UUID empresaCredoraId, String motivoSanitizado, UUID usuarioId) {}

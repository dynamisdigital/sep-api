package com.dynamis.sep_api.credores.domain.event;

import java.util.UUID;

/**
 * Sugestao de matching confirmada por decisao assistida (Sprint 30). {@code motivoSanitizado} ja
 * chega tratado pelo dominio (nunca texto bruto). A confirmacao nao dispara aporte, Pix ou escrow.
 */
public record MatchingCredoraConfirmadoEvent(
        UUID matchingId, UUID operacaoId, UUID empresaCredoraId, String motivoSanitizado, UUID usuarioId) {}

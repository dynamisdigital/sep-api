package com.dynamis.sep_api.credores.domain.event;

import java.util.UUID;

/**
 * Evento publicado quando uma operacao financiada e associada a carteira de uma credora
 * (Sprint 17). {@code usuarioId} e o ator que disparou a associacao (pode ser nulo se sistemico).
 */
public record OperacaoFinanciadaAssociadaEvent(
        UUID operacaoId, UUID empresaCredoraId, UUID contratoId, UUID usuarioId) {}

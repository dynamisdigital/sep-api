package com.dynamis.sep_api.credores.domain.event;

import java.util.UUID;

/** Evento publicado quando uma credora cancela seu interesse numa oportunidade (Sprint 17). */
public record InteresseCredoraCanceladoEvent(
        UUID interesseId, UUID empresaCredoraId, UUID oportunidadeId, UUID usuarioId) {}

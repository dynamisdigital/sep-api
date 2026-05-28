package com.dynamis.sep_api.credores.domain.event;

import java.util.UUID;

/** Evento publicado quando uma credora manifesta interesse numa oportunidade (Sprint 17). */
public record InteresseCredoraRegistradoEvent(
        UUID interesseId, UUID empresaCredoraId, UUID oportunidadeId, UUID usuarioId) {}

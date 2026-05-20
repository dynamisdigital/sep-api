package com.dynamis.sep_api.contratos.domain.event;

import java.util.UUID;

/** Disparado quando financeiro/admin cancela contrato antes do aceite. */
public record ContratoCanceladoEvent(
        UUID contratoId, UUID propostaId, UUID tomadorId, UUID canceladoPorId, String justificativa) {}

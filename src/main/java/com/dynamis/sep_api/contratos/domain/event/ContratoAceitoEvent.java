package com.dynamis.sep_api.contratos.domain.event;

import java.util.UUID;

/** Disparado quando tomador aceita versao vigente do contrato. */
public record ContratoAceitoEvent(
        UUID contratoId,
        UUID propostaId,
        UUID tomadorId,
        UUID versaoId,
        int numeroVersao,
        String hashSha256,
        String ipOrigem,
        String userAgentOrigem) {}

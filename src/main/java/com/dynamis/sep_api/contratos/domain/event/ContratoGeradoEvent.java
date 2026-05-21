package com.dynamis.sep_api.contratos.domain.event;

import java.util.UUID;

/** Disparado quando primeira versao do contrato e gerada. */
public record ContratoGeradoEvent(
        UUID contratoId, UUID propostaId, UUID tomadorId, UUID versaoId, int numeroVersao, String hashSha256) {}

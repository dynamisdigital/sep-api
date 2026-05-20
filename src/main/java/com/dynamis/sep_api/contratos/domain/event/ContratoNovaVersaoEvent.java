package com.dynamis.sep_api.contratos.domain.event;

import java.util.UUID;

/** Disparado quando nova versao e gerada para contrato existente (re-geracao pre-aceite). */
public record ContratoNovaVersaoEvent(
        UUID contratoId, UUID propostaId, UUID tomadorId, UUID versaoId, int numeroVersao, String hashSha256) {}

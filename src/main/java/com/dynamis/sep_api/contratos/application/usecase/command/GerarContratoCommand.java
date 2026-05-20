package com.dynamis.sep_api.contratos.application.usecase.command;

import java.util.Objects;
import java.util.UUID;

/** Comando para gerar contrato a partir de proposta de credito aprovada (Sprint 10 Task 10.3). */
public record GerarContratoCommand(UUID propostaId) {

    public GerarContratoCommand {
        Objects.requireNonNull(propostaId, "propostaId obrigatoria");
    }
}

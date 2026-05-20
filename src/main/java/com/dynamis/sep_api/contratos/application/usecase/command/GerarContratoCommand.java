package com.dynamis.sep_api.contratos.application.usecase.command;

import java.util.Objects;
import java.util.UUID;

/**
 * Comando para gerar contrato a partir de proposta de credito aprovada (Sprint 10 Task 10.3).
 *
 * <p>{@code parecerOrigemId} (opcional) liga a versao gerada ao parecer (Sprint 8) que originou a
 * aprovacao. Permite idempotencia no fluxo automatico: se {@link
 * com.dynamis.sep_api.contratos.application.listener.PropostaAprovadaListener} processar o mesmo
 * {@code PropostaAprovadaEvent} duas vezes (replay), o use case detecta versao vigente com o
 * mesmo parecer e retorna o contrato existente sem gerar versao duplicada.
 *
 * <p>{@code null} indica geracao "manual" (sem evento associado) — caminho da Task 10.6 quando
 * implementada. Versoes sem parecerOrigem nao sao consideradas idempotentes; cada chamada manual
 * gera nova versao.
 */
public record GerarContratoCommand(UUID propostaId, UUID parecerOrigemId) {

    public GerarContratoCommand {
        Objects.requireNonNull(propostaId, "propostaId obrigatoria");
    }

    public static GerarContratoCommand manual(UUID propostaId) {
        return new GerarContratoCommand(propostaId, null);
    }
}

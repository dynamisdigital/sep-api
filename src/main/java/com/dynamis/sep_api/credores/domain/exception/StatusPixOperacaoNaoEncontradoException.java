package com.dynamis.sep_api.credores.domain.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

/**
 * Ausencia neutra (HTTP 404) da leitura do status Pix de uma operacao da carteira da credora (Sprint
 * 26 — Gate P3). Usuario sem credora, operacao de outra credora, operacao inexistente e operacao sem
 * desembolso Pix lancam esta mesma excecao generica, sem identificador, impedindo enumeracao — ao
 * contrario das excecoes de carteira que ecoam o UUID.
 */
public class StatusPixOperacaoNaoEncontradoException extends RecursoNaoEncontradoException {

    public static final String CODIGO = "CRD-404-005";

    public StatusPixOperacaoNaoEncontradoException() {
        super(CODIGO, "Status Pix da operacao nao encontrado");
    }
}

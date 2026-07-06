package com.dynamis.sep_api.pix.domain.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

/**
 * Ausencia neutra (HTTP 404) das leituras Pix owner-scoped do tomador (Sprint 26 — Gates P1/P2).
 * Contrato/parcela inexistente, recurso de outro tomador e ausencia de estado Pix lancam esta mesma
 * excecao generica, sem identificador, impedindo enumeracao de recursos alheios.
 */
public class PixLeituraNaoEncontradaException extends RecursoNaoEncontradoException {

    public PixLeituraNaoEncontradaException() {
        super("PIX-404-001", "Recurso Pix nao encontrado");
    }
}

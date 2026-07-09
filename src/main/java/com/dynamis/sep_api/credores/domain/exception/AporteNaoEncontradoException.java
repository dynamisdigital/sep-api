package com.dynamis.sep_api.credores.domain.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

/**
 * Ausencia neutra (HTTP 404) do aporte na reconciliacao (Sprint 29 Task 29.5). Referencia
 * desconhecida nao revela se existe aporte, operacao ou credora — mensagem fixa sem identificador.
 */
public class AporteNaoEncontradoException extends RecursoNaoEncontradoException {

    public static final String CODIGO = "CRD-404-007";

    public AporteNaoEncontradoException() {
        super(CODIGO, "Aporte nao encontrado para reconciliacao");
    }
}

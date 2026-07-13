package com.dynamis.sep_api.credores.domain.exception;

import com.dynamis.sep_api.shared.exception.ConflitoException;

/**
 * Decisao sobre sugestao de matching ja decidida (HTTP 409, Sprint 30). Replay e decisoes
 * concorrentes caem aqui apos a serializacao pelo lock. Mensagem fixa — nao ecoa id, par nem o
 * status atual.
 */
public class MatchingDecisaoConflitanteException extends ConflitoException {

    public static final String CODIGO = "CRD-409-007";

    public MatchingDecisaoConflitanteException() {
        super(CODIGO, "Sugestao de matching ja decidida");
    }
}

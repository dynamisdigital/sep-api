package com.dynamis.sep_api.contratos.domain.exception;

import com.dynamis.sep_api.shared.exception.AcessoNegadoException;

import java.util.UUID;

/**
 * Tomador autenticado nao e dono do contrato (HTTP 403). Apenas o tomador titular pode aceitar
 * seu proprio contrato. Operacoes administrativas/financeiras usam roles + endpoints proprios.
 */
public class ContratoOwnershipException extends AcessoNegadoException {

    public static final String CODIGO = "CTR-403-001";

    public ContratoOwnershipException(UUID contratoId) {
        super(CODIGO, "Tomador autenticado nao e dono do contrato " + contratoId);
    }
}

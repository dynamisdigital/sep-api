package com.dynamis.sep_api.credito.domain.exception;

import com.dynamis.sep_api.shared.exception.AcessoNegadoException;

/**
 * Recurso de credito (proposta, onboarding referenciado) pertence a outro tomador (HTTP 403).
 * Diferente de {@link com.dynamis.sep_api.credito.domain.exception.PropostaNaoEncontradaException}
 * (404), aqui o caller sabe que o recurso existe — apenas nao tem acesso.
 */
public class OwnershipPropostaException extends AcessoNegadoException {

    public static final String CODIGO = "CRD-403-001";

    public OwnershipPropostaException(String mensagem) {
        super(CODIGO, mensagem);
    }
}

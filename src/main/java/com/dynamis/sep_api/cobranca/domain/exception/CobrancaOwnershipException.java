package com.dynamis.sep_api.cobranca.domain.exception;

import com.dynamis.sep_api.shared.exception.AcessoNegadoException;

import java.util.UUID;

/**
 * Cliente tenta acessar agenda/parcela de contrato que nao eh dele (HTTP 403). Operadores
 * {@code FINANCEIRO} e {@code ADMIN} ignoram essa checagem.
 */
public class CobrancaOwnershipException extends AcessoNegadoException {

    public static final String CODIGO = "COB-403-001";

    public CobrancaOwnershipException(UUID contratoId) {
        super(CODIGO, "Recurso de cobranca pertence a outro tomador: contrato " + contratoId);
    }
}

package com.dynamis.sep_api.credito.domain.exception;

import com.dynamis.sep_api.shared.exception.ConflitoException;

/**
 * Ja existe consentimento Open Finance {@code PENDENTE} para a proposta (HTTP 409). V17 unique
 * parcial garante 1 PENDENTE por proposta — caller deve aguardar o atual ser autorizado/negado/
 * expirado antes de criar novo.
 */
public class ConsentimentoAtivoException extends ConflitoException {

    public static final String CODIGO = "CRD-409-002";

    public ConsentimentoAtivoException() {
        super(CODIGO, "Ja existe consentimento Open Finance PENDENTE para esta proposta");
    }
}

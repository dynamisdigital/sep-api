package com.dynamis.sep_api.credores.domain.exception;

import com.dynamis.sep_api.shared.exception.ConflitoException;

/**
 * Reuso de {@code Idempotency-Key} com payload divergente na mesma operacao (HTTP 409, Sprint 29).
 * Mensagem fixa — nao ecoa a chave nem valores para nao vazar dado de outra requisicao.
 */
public class AporteConflitanteException extends ConflitoException {

    public static final String CODIGO = "CRD-409-005";

    public AporteConflitanteException() {
        super(CODIGO, "Idempotency-Key ja utilizada com dados divergentes para a operacao");
    }
}

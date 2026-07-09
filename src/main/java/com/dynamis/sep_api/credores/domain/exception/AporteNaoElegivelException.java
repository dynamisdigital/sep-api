package com.dynamis.sep_api.credores.domain.exception;

import com.dynamis.sep_api.shared.exception.ConflitoException;

/**
 * Operacao nao elegivel para aporte (HTTP 409, contrato REST da Sprint 29): operacao encerrada,
 * contrato inexistente ou contrato nao {@code ASSINADO}. Mensagem fixa sem UUID nem detalhe de
 * escrow/provider.
 */
public class AporteNaoElegivelException extends ConflitoException {

    public static final String CODIGO = "CRD-409-004";

    public AporteNaoElegivelException() {
        super(CODIGO, "Operacao nao elegivel para aporte");
    }
}

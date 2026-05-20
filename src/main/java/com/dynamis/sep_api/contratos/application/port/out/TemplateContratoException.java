package com.dynamis.sep_api.contratos.application.port.out;

import com.dynamis.sep_api.shared.exception.OperacaoNaoProcessavelException;

/**
 * Falha durante renderizacao de template de contrato. Mapeada para 422 (operacao nao processavel)
 * pelo {@code ApiExceptionHandler}.
 */
public class TemplateContratoException extends OperacaoNaoProcessavelException {

    public static final String CODIGO = "CTR-422-001";

    public TemplateContratoException(String mensagem) {
        super(CODIGO, mensagem);
    }

    public TemplateContratoException(String mensagem, Throwable causa) {
        super(CODIGO, mensagem);
        initCause(causa);
    }
}

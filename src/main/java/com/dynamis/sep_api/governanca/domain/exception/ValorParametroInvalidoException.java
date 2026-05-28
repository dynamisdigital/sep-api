package com.dynamis.sep_api.governanca.domain.exception;

import com.dynamis.sep_api.shared.exception.ValidacaoException;

/** Valor incompativel com o tipo do parametro operacional (HTTP 400). */
public class ValorParametroInvalidoException extends ValidacaoException {

    public static final String CODIGO = "GOV-400-001";

    public ValorParametroInvalidoException(String chave, String valor, String tipo) {
        super(CODIGO, "Valor '" + valor + "' invalido para o parametro '" + chave + "' do tipo " + tipo);
    }
}

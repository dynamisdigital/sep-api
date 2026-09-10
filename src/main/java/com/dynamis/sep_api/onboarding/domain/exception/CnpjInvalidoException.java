package com.dynamis.sep_api.onboarding.domain.exception;

import com.dynamis.sep_api.shared.exception.ValidacaoException;

/**
 * CNPJ ausente ou invalido no inicio do onboarding PJ (HTTP 400).
 *
 * <p>Uma condicao (ADR 0020 §3): a acao do cliente e a mesma nos dois casos — informar um CNPJ valido
 * —, e a mensagem diz qual deles ocorreu. Antes eram dois lancamentos inline do mesmo codigo.
 */
public class CnpjInvalidoException extends ValidacaoException {

    public static final String CODIGO = "ONB-400-006";

    private CnpjInvalidoException(String mensagem) {
        super(CODIGO, mensagem);
    }

    public static CnpjInvalidoException obrigatorio() {
        return new CnpjInvalidoException("CNPJ e obrigatorio");
    }

    /** @param motivo mensagem do value object {@code Cnpj}, repassada sem alteracao ao cliente */
    public static CnpjInvalidoException invalido(String motivo) {
        return new CnpjInvalidoException(motivo);
    }
}

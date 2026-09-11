package com.dynamis.sep_api.onboarding.domain.exception;

import com.dynamis.sep_api.shared.exception.ValidacaoException;

/**
 * CPF ausente ou invalido no inicio do onboarding PF (HTTP 400).
 *
 * <p>Uma condicao (ADR 0020 §3): a acao do cliente e a mesma nos dois casos — informar um CPF valido
 * —, e a mensagem diz qual deles ocorreu. Antes eram dois lancamentos inline do mesmo codigo, que o
 * catalogo contava como duas condicoes.
 */
public class CpfInvalidoException extends ValidacaoException {

    public static final String CODIGO = "ONB-400-002";

    private CpfInvalidoException(String mensagem) {
        super(CODIGO, mensagem);
    }

    public static CpfInvalidoException obrigatorio() {
        return new CpfInvalidoException("CPF e obrigatorio");
    }

    /** @param motivo mensagem do value object {@code Cpf}, repassada sem alteracao ao cliente */
    public static CpfInvalidoException invalido(String motivo) {
        return new CpfInvalidoException(motivo);
    }
}

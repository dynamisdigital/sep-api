package com.dynamis.sep_api.onboarding.domain.exception;

import com.dynamis.sep_api.shared.exception.ValidacaoException;

/**
 * Operacao exclusiva de solicitacao PJ chamada com solicitacao PF (HTTP 400).
 *
 * <p>Uma condicao, um dono (ADR 0020 §3): antes, dois use cases lancavam o mesmo literal com a mesma
 * mensagem, e o codigo nao podia ser publicado por ter dois donos.
 */
public class SolicitacaoNaoEmpresaException extends ValidacaoException {

    public static final String CODIGO = "ONB-400-008";

    public SolicitacaoNaoEmpresaException() {
        super(CODIGO, "Solicitacao nao e do tipo EMPRESA");
    }
}

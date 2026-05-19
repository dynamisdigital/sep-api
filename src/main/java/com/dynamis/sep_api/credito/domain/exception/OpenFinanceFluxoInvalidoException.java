package com.dynamis.sep_api.credito.domain.exception;

import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.shared.exception.OperacaoNaoProcessavelException;

/**
 * Proposta nao admite operacao Open Finance no estado atual (HTTP 422). Ex.: tentativa de iniciar
 * consentimento em proposta {@code APROVADA}/{@code REJEITADA}.
 */
public class OpenFinanceFluxoInvalidoException extends OperacaoNaoProcessavelException {

    public static final String CODIGO = "CRD-422-002";

    public OpenFinanceFluxoInvalidoException(StatusProposta statusAtual) {
        super(CODIGO, "Open Finance nao aceito para proposta no status " + statusAtual);
    }
}

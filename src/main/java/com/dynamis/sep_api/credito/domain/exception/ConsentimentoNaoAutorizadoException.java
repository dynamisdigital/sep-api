package com.dynamis.sep_api.credito.domain.exception;

import com.dynamis.sep_api.credito.domain.vo.StatusConsentimento;
import com.dynamis.sep_api.shared.exception.OperacaoNaoProcessavelException;

/**
 * Operacao exige consentimento Open Finance em {@link StatusConsentimento#AUTORIZADO}
 * (HTTP 422). Sprint 9 — evita uso de {@code OpenFinanceFluxoInvalidoException} com
 * {@code StatusProposta} como proxy.
 */
public class ConsentimentoNaoAutorizadoException extends OperacaoNaoProcessavelException {

    public static final String CODIGO = "CRD-422-003";

    public ConsentimentoNaoAutorizadoException(StatusConsentimento atual) {
        super(CODIGO, "Consentimento Open Finance nao autorizado; status atual: " + atual);
    }
}

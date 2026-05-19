package com.dynamis.sep_api.credito.domain.exception;

import com.dynamis.sep_api.credito.domain.vo.StatusConsentimento;

/**
 * Transicao invalida no agregado {@code ConsentimentoOpenFinance} (Sprint 9).
 */
public class ConsentimentoInvalidoException extends RuntimeException {

    public ConsentimentoInvalidoException(String operacao, StatusConsentimento atual, StatusConsentimento alvo) {
        super("Transicao invalida (" + operacao + "): " + atual + " -> " + alvo);
    }

    public ConsentimentoInvalidoException(String mensagem) {
        super(mensagem);
    }
}

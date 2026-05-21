package com.dynamis.sep_api.contratos.application.service.ccb;

/**
 * Falha na geracao do PDF da CCB (Sprint 11 Task 11.3). Deve abortar envio para o provider de
 * assinatura digital — nunca enviar documento parcial.
 */
public class CcbGeracaoException extends RuntimeException {

    public CcbGeracaoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}

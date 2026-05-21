package com.dynamis.sep_api.contratos.infrastructure.adapter.assinatura;

/**
 * Resposta da Clicksign sem campos minimos esperados (Sprint 11 Task 11.4). Indica contrato HTTP
 * quebrado entre SEP e Clicksign — propagada pro use case decidir se rollback do envio cabe.
 */
public class ClicksignRespostaInvalidaException extends RuntimeException {

    public ClicksignRespostaInvalidaException(String mensagem) {
        super(mensagem);
    }
}

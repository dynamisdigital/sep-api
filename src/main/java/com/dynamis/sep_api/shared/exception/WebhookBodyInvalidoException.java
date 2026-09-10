package com.dynamis.sep_api.shared.exception;

/**
 * Body de webhook que nao e JSON valido (HTTP 400). Uma condicao para todos os webhooks (ADR 0020
 * §3), antes repetida com o prefixo de cada modulo.
 */
public class WebhookBodyInvalidoException extends ValidacaoException {

    public static final String CODIGO = "WHK-400-005";

    public WebhookBodyInvalidoException() {
        super(CODIGO, "Body do webhook nao e JSON valido");
    }
}

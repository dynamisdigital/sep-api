package com.dynamis.sep_api.shared.exception;

/**
 * Webhook recebido sem body (HTTP 400). Uma condicao para todos os webhooks (ADR 0020 §3), antes
 * repetida com o prefixo de cada modulo.
 */
public class WebhookBodyObrigatorioException extends ValidacaoException {

    public static final String CODIGO = "WHK-400-004";

    public WebhookBodyObrigatorioException() {
        super(CODIGO, "Body do webhook e obrigatorio");
    }
}

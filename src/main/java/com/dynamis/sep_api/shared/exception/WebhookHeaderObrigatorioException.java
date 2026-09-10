package com.dynamis.sep_api.shared.exception;

/**
 * Header obrigatorio ausente no recebimento de webhook (HTTP 400).
 *
 * <p>Uma condicao para todos os webhooks (ADR 0020 §3): a acao do provedor e a mesma — enviar o
 * header indicado —, e a mensagem nomeia qual. Antes, seis controllers faziam a mesma checagem com o
 * prefixo do proprio modulo, e a mesma condicao tinha ate seis codigos.
 */
public class WebhookHeaderObrigatorioException extends ValidacaoException {

    public static final String CODIGO = "WHK-400-003";

    /**
     * @param header nome do header como o provedor deve envia-lo, com o alias entre parenteses quando
     *     houver — compoe a mensagem sem alterar o texto que cada controller ja devolvia
     */
    public WebhookHeaderObrigatorioException(String header) {
        super(CODIGO, "Header " + header + " e obrigatorio");
    }
}

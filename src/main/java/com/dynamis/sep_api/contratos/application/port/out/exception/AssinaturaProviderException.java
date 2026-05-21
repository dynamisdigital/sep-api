package com.dynamis.sep_api.contratos.application.port.out.exception;

/**
 * Excecao base do {@link com.dynamis.sep_api.contratos.application.port.out.AssinaturaDigitalProvider}
 * (Sprint 11). Os adapters concretos (ex.: Clicksign) traduzem erros HTTP/IO para esta hierarquia
 * — use cases e webhooks tratam apenas estes tipos, sem acoplamento a {@code RestClientResponseException}
 * ou outras exceptions de framework.
 */
public class AssinaturaProviderException extends RuntimeException {

    public AssinaturaProviderException(String mensagem) {
        super(mensagem);
    }

    public AssinaturaProviderException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}

package com.dynamis.sep_api.pix.application.port.out.exception;

/**
 * Excecao base do {@link com.dynamis.sep_api.pix.application.port.out.PixProvider} (Sprint 19). Os
 * adapters concretos (ex.: Celcoin) traduzem erros HTTP/IO e respostas invalidas para esta
 * hierarquia — use cases tratam apenas estes tipos, sem acoplamento a {@code
 * RestClientResponseException} ou outras exceptions de framework.
 */
public class PixProviderException extends RuntimeException {

    public PixProviderException(String mensagem) {
        super(mensagem);
    }

    public PixProviderException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}

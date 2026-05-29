package com.dynamis.sep_api.escrow.application.port.out.exception;

/**
 * Excecao base do {@link com.dynamis.sep_api.escrow.application.port.out.EscrowProvider} (Sprint
 * 19). Os adapters concretos (ex.: Celcoin) traduzem erros HTTP/IO e respostas invalidas para esta
 * hierarquia — use cases tratam apenas estes tipos, sem acoplamento a {@code
 * RestClientResponseException} ou outras exceptions de framework.
 */
public class EscrowProviderException extends RuntimeException {

    public EscrowProviderException(String mensagem) {
        super(mensagem);
    }

    public EscrowProviderException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}

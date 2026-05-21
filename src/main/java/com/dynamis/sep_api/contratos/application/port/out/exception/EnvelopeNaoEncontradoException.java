package com.dynamis.sep_api.contratos.application.port.out.exception;

/**
 * Envelope nao localizado no provider de assinatura (Sprint 11). HTTP 404 do adapter real ou
 * lookup em fake sem estado pra esse id.
 */
public class EnvelopeNaoEncontradoException extends AssinaturaProviderException {

    public EnvelopeNaoEncontradoException(String idEnvelopeExterno) {
        super("Envelope nao encontrado no provider: " + idEnvelopeExterno);
    }

    public EnvelopeNaoEncontradoException(String idEnvelopeExterno, Throwable causa) {
        super("Envelope nao encontrado no provider: " + idEnvelopeExterno, causa);
    }
}

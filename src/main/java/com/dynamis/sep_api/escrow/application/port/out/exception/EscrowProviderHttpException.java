package com.dynamis.sep_api.escrow.application.port.out.exception;

/**
 * Falha HTTP retornada pelo provider de escrow (Sprint 19). Carrega {@code statusCode} pra use
 * cases decidirem tratamento (4xx -> bug de contrato/permissao; 5xx -> retry/retry-later).
 */
public class EscrowProviderHttpException extends EscrowProviderException {

    private final int statusCode;

    public EscrowProviderHttpException(int statusCode, String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }

    public boolean isServerError() {
        return statusCode >= 500 && statusCode < 600;
    }
}

package com.dynamis.sep_api.contratos.application.port.out.exception;

/**
 * Falha HTTP retornada pelo provider de assinatura (Sprint 11). Carrega {@code statusCode} pra
 * use cases decidirem tratamento (4xx -> bug de contrato/permissao; 5xx -> retry/retry-later).
 */
public class AssinaturaProviderHttpException extends AssinaturaProviderException {

    private final int statusCode;

    public AssinaturaProviderHttpException(int statusCode, String mensagem, Throwable causa) {
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

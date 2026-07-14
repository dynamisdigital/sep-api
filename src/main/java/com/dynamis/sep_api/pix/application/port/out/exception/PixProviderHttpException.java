package com.dynamis.sep_api.pix.application.port.out.exception;

import com.dynamis.sep_api.shared.integration.ProviderHttpFault;

/**
 * Falha HTTP retornada pelo provider Pix (Sprint 19). Carrega {@code statusCode} pra use cases
 * decidirem tratamento (4xx -> bug de contrato/permissao; 5xx -> retry/retry-later). Implementa
 * {@link ProviderHttpFault} (Sprint 32): o retry compartilhado reentra somente em 5xx.
 */
public class PixProviderHttpException extends PixProviderException implements ProviderHttpFault {

    private final int statusCode;

    public PixProviderHttpException(int statusCode, String mensagem, Throwable causa) {
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

package com.dynamis.sep_api.contratos.domain.exception;

import com.dynamis.sep_api.shared.exception.ConflitoException;

import java.util.UUID;

/**
 * Documento assinado ainda nao disponivel — contrato nao esta em {@code ASSINADO} ou envelope/
 * documento ausentes (Sprint 11). Estende {@link ConflitoException} para mapear em HTTP 409 via
 * {@code ApiExceptionHandler} (padrao do projeto).
 */
public class ContratoAssinaturaIndisponivelException extends ConflitoException {

    private static final String CODIGO = "CTR-409-003";

    public ContratoAssinaturaIndisponivelException(UUID contratoId, String detalhe) {
        super(CODIGO, "Documento assinado indisponivel para contrato " + contratoId + ": " + detalhe);
    }
}

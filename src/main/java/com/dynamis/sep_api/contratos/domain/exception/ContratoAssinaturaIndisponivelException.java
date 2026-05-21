package com.dynamis.sep_api.contratos.domain.exception;

import java.util.UUID;

/**
 * Documento assinado ainda nao disponivel — contrato nao esta em {@code ASSINADO} ou envelope/
 * documento ausentes (Sprint 11). Use case de download propaga 409.
 */
public class ContratoAssinaturaIndisponivelException extends RuntimeException {

    public ContratoAssinaturaIndisponivelException(UUID contratoId, String detalhe) {
        super("Documento assinado indisponivel para contrato " + contratoId + ": " + detalhe);
    }
}

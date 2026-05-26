package com.dynamis.sep_api.backoffice.domain.exception;

import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;

/**
 * Tipo de chamada a provider sem strategy registrada no
 * {@code ProviderReprocessadorDispatcher} (Sprint 14 Task 14.4). Mapeada para HTTP 400 via
 * {@code ApiExceptionHandler} — substitui o uso generico de {@link UnsupportedOperationException}
 * (fix review manual Task 14.7).
 */
public class TipoReprocessoNaoSuportadoException extends RuntimeException {

    public static final String CODIGO = "BOF-400-002";

    public TipoReprocessoNaoSuportadoException(TipoChamadaProvider tipo) {
        super("Tipo de reprocesso nao suportado: " + tipo);
    }
}

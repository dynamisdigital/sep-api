package com.dynamis.sep_api.onboarding.web.controller;

import com.dynamis.sep_api.shared.exception.ValidacaoException;

/**
 * Falha ao ler os bytes do arquivo recebido no upload (HTTP 400).
 *
 * <p>Condicao de adaptador, e nao de dominio: por isso mora no pacote dos dois controllers que a
 * lancam, com um dono so (ADR 0020 §3).
 */
final class ArquivoIlegivelException extends ValidacaoException {

    static final String CODIGO = "ONB-400-007";

    ArquivoIlegivelException() {
        super(CODIGO, "Falha ao ler bytes do arquivo");
    }
}

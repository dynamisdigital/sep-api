package com.dynamis.sep_api.contratos.application.service.ccb;

import com.dynamis.sep_api.shared.exception.OperacaoNaoProcessavelException;

/**
 * Falha na geracao do PDF da CCB (Sprint 11 Task 11.3). Aborta envio para o provider de
 * assinatura digital — nunca enviar documento parcial. Mapeada para HTTP 422 (Unprocessable
 * Entity) via {@code ApiExceptionHandler} — payload sintaticamente valido mas operacao nao
 * pode ser executada (template ausente, PDFBox falhou, dados cadastrais invalidos).
 */
public class CcbGeracaoException extends OperacaoNaoProcessavelException {

    private static final String CODIGO = "CTR-422-CCB-001";

    public CcbGeracaoException(String mensagem, Throwable causa) {
        super(CODIGO, mensagem);
        if (causa != null) {
            initCause(causa);
        }
    }
}

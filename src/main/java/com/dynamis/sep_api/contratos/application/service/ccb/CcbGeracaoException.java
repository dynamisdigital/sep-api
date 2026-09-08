package com.dynamis.sep_api.contratos.application.service.ccb;

import com.dynamis.sep_api.shared.exception.OperacaoNaoProcessavelException;

/**
 * Falha na geracao do PDF da CCB (Sprint 11 Task 11.3). Aborta envio para o provider de
 * assinatura digital — nunca enviar documento parcial. Mapeada para HTTP 422 (Unprocessable
 * Entity) via {@code ApiExceptionHandler} — payload sintaticamente valido mas operacao nao
 * pode ser executada (template ausente, PDFBox falhou, dados cadastrais invalidos).
 */
public class CcbGeracaoException extends OperacaoNaoProcessavelException {

    /**
     * Era {@code CTR-422-CCB-001} ate a Sprint 36 Task 36.3. O sufixo semantico era a unica forma nao
     * canonica dentro do subconjunto que esta sprint publica, e a janela para corrigir fecha aqui:
     * enquanto nada consome o codigo, renomear e edicao de uma linha; depois de publicado no OpenAPI,
     * vira mudanca de contrato, com snapshot a regenerar no {@code sep-app} e {@code knownGap} a criar.
     *
     * <p>{@code 004} e o proximo livre da faixa — {@code CTR-422-001..003} ja tem dono, conferido no
     * inventario do Gate 36.0 sobre {@code src} inteiro. Os onze sufixos semanticos do modulo
     * {@code PIX} <b>nao</b> foram normalizados junto: la a convencao semantica e majoritaria (28 de
     * 31), entao escolher entre as duas convencoes e decisao de taxonomia, e pertence a Sprint 37.
     */
    private static final String CODIGO = "CTR-422-004";

    public CcbGeracaoException(String mensagem, Throwable causa) {
        super(CODIGO, mensagem);
        if (causa != null) {
            initCause(causa);
        }
    }
}

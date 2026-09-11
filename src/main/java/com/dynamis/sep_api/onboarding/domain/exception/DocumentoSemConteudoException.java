package com.dynamis.sep_api.onboarding.domain.exception;

import com.dynamis.sep_api.shared.exception.ValidacaoException;

/**
 * Documento enviado sem conteudo (HTTP 400).
 *
 * <p>Uma condicao (ADR 0020 §3), lancada em duas camadas: o controller barra o upload sem arquivo e o
 * use case barra o comando com bytes vazios. A acao do cliente e a mesma — enviar o arquivo —, e as
 * duas mensagens que ja existiam seguem identicas.
 */
public class DocumentoSemConteudoException extends ValidacaoException {

    public static final String CODIGO = "ONB-400-017";

    private DocumentoSemConteudoException(String mensagem) {
        super(CODIGO, mensagem);
    }

    /** Upload multipart sem o arquivo. */
    public static DocumentoSemConteudoException arquivoAusente() {
        return new DocumentoSemConteudoException("Arquivo do documento e obrigatorio");
    }

    /** Comando de upload com bytes vazios. */
    public static DocumentoSemConteudoException conteudoVazio() {
        return new DocumentoSemConteudoException("Conteudo do documento e obrigatorio");
    }
}

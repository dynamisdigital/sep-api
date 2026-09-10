package com.dynamis.sep_api.pix.domain.exception;

import com.dynamis.sep_api.pix.domain.vo.TipoChavePix;
import com.dynamis.sep_api.shared.exception.ValidacaoException;

/**
 * Chave Pix ausente ou invalida para o tipo (HTTP 400).
 *
 * <p>Uma condicao so (ADR 0020 §3): em todas as variantes o cliente corrige o valor da chave. Antes,
 * o normalizador e o desembolso eram dois donos do mesmo codigo semantico {@code PIX-400-CHAVE}. As
 * mensagens sao as que cada ponto ja devolvia, e nenhuma ecoa o valor bruto.
 */
public class ChavePixInvalidaException extends ValidacaoException {

    public static final String CODIGO = "PIX-400-003";

    private ChavePixInvalidaException(String mensagem) {
        super(CODIGO, mensagem);
    }

    /** Cadastro de chave sem valor. */
    public static ChavePixInvalidaException valorObrigatorio() {
        return new ChavePixInvalidaException("valor da chave Pix obrigatorio.");
    }

    /** Desembolso sem chave de destino. */
    public static ChavePixInvalidaException destinoObrigatorio() {
        return new ChavePixInvalidaException("chave Pix destino obrigatoria.");
    }

    /** Valor que nao passa na normalizacao do tipo; a mensagem nomeia o tipo, nunca o valor. */
    public static ChavePixInvalidaException paraOTipo(TipoChavePix tipo) {
        return new ChavePixInvalidaException("chave Pix invalida para o tipo " + tipo + ".");
    }
}

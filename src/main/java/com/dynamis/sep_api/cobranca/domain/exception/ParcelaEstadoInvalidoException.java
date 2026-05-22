package com.dynamis.sep_api.cobranca.domain.exception;

import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.shared.exception.ConflitoException;

/** Tentativa de transicao viola maquina de estados de {@link StatusParcela} (HTTP 409). */
public class ParcelaEstadoInvalidoException extends ConflitoException {

    public static final String CODIGO = "COB-409-001";

    public ParcelaEstadoInvalidoException(String mensagem) {
        super(CODIGO, mensagem);
    }

    public ParcelaEstadoInvalidoException(String operacao, StatusParcela atual) {
        super(CODIGO, "Operacao '" + operacao + "' nao permitida no estado " + atual);
    }
}

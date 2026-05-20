package com.dynamis.sep_api.contratos.domain.exception;

import com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao;
import com.dynamis.sep_api.shared.exception.ConflitoException;

/**
 * Tentativa de transicao viola maquina de estados de {@link StatusFormalizacao} (HTTP 409
 * Conflict).
 */
public class ContratoEstadoInvalidoException extends ConflitoException {

    public static final String CODIGO = "CTR-409-001";

    public ContratoEstadoInvalidoException(String mensagem) {
        super(CODIGO, mensagem);
    }

    public ContratoEstadoInvalidoException(String operacao, StatusFormalizacao atual) {
        super(CODIGO, "Operacao '" + operacao + "' nao permitida no estado " + atual);
    }
}

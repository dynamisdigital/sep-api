package com.dynamis.sep_api.contratos.domain.exception;

import com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao;

/**
 * Levantada quando tentativa de transicao viola maquina de estados de {@link StatusFormalizacao}.
 * Mapeada para 409 Conflict no handler global.
 */
public class ContratoEstadoInvalidoException extends RuntimeException {

    public ContratoEstadoInvalidoException(String mensagem) {
        super(mensagem);
    }

    public ContratoEstadoInvalidoException(String operacao, StatusFormalizacao atual) {
        super("Operacao '" + operacao + "' nao permitida no estado " + atual);
    }
}

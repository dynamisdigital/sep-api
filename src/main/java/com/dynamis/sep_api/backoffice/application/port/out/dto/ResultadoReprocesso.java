package com.dynamis.sep_api.backoffice.application.port.out.dto;

import com.dynamis.sep_api.backoffice.domain.vo.StatusReprocesso;

/**
 * Retorno do adapter de reprocesso (Sprint 14 Task 14.4). Sempre eh {@code SUCESSO} ou {@code FALHA}
 * — {@code PENDENTE} eh apenas o estado inicial do registro persistido.
 */
public record ResultadoReprocesso(StatusReprocesso status, String mensagemTecnica) {

    public static ResultadoReprocesso sucesso(String mensagem) {
        return new ResultadoReprocesso(StatusReprocesso.SUCESSO, mensagem);
    }

    public static ResultadoReprocesso falha(String mensagem) {
        return new ResultadoReprocesso(StatusReprocesso.FALHA, mensagem);
    }
}

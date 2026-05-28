package com.dynamis.sep_api.credores.application.port.out;

import java.util.List;

/**
 * Porta de saida orientada a necessidade do modulo {@code credores}: lista propostas elegiveis
 * (aprovadas/formalizadas) para materializar oportunidades de investimento (Sprint 17). O adapter
 * traduz a leitura do modulo {@code credito}/{@code contratos} sem expor entidades JPA.
 */
public interface ConsultarPropostasElegiveisParaCredoraPort {

    List<PropostaElegivelView> listarElegiveis();
}

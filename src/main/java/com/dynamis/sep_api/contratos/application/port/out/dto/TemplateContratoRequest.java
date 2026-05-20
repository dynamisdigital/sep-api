package com.dynamis.sep_api.contratos.application.port.out.dto;

import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;

import java.util.Map;
import java.util.Objects;

/**
 * Solicitacao de renderizacao de template de contrato (Sprint 10 Task 10.2). Encapsula o tipo do
 * contrato (que define template a usar) e o mapa de variaveis. A port nao deve vazar classes do
 * engine (Thymeleaf, etc).
 */
public record TemplateContratoRequest(TipoContrato tipo, Map<String, Object> variaveis) {

    public TemplateContratoRequest {
        Objects.requireNonNull(tipo, "tipo obrigatorio");
        Objects.requireNonNull(variaveis, "variaveis obrigatorias");
        variaveis = Map.copyOf(variaveis);
    }
}

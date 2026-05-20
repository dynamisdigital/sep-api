package com.dynamis.sep_api.contratos.application.port.out.dto;

import java.util.List;
import java.util.Objects;

/**
 * Resultado da renderizacao do template. Contem o conteudo textual final e a lista de clausulas
 * parseadas (uma por marcador {@code CLAUSULA N - TITULO}).
 */
public record TemplateContratoResponse(String conteudoTexto, List<ClausulaRenderizada> clausulas) {

    public TemplateContratoResponse {
        Objects.requireNonNull(conteudoTexto, "conteudoTexto obrigatorio");
        if (conteudoTexto.isBlank()) {
            throw new IllegalArgumentException("conteudoTexto nao pode ser vazio");
        }
        Objects.requireNonNull(clausulas, "clausulas obrigatorias");
        clausulas = List.copyOf(clausulas);
    }
}

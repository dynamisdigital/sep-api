package com.dynamis.sep_api.credores.application.service;

import com.dynamis.sep_api.credores.domain.vo.CriterioMatchingCredoraOperacao;

import java.util.List;
import java.util.Objects;

/**
 * Resultado da avaliacao de elegibilidade de matching (Sprint 30 Task 30.1). Quando elegivel,
 * carrega os criterios atendidos em ordem deterministica — base do {@code criteriosSnapshot}
 * persistido na sugestao. Quando inelegivel, carrega apenas o primeiro criterio violado.
 */
public record ResultadoElegibilidadeMatching(
        boolean elegivel,
        CriterioMatchingCredoraOperacao criterioViolado,
        List<CriterioMatchingCredoraOperacao> criteriosAtendidos) {

    public static ResultadoElegibilidadeMatching elegivel(List<CriterioMatchingCredoraOperacao> criteriosAtendidos) {
        Objects.requireNonNull(criteriosAtendidos, "criterios atendidos obrigatorios");
        if (criteriosAtendidos.isEmpty()) {
            throw new IllegalArgumentException("resultado elegivel exige ao menos um criterio atendido");
        }
        return new ResultadoElegibilidadeMatching(true, null, List.copyOf(criteriosAtendidos));
    }

    public static ResultadoElegibilidadeMatching inelegivel(CriterioMatchingCredoraOperacao criterioViolado) {
        Objects.requireNonNull(criterioViolado, "criterio violado obrigatorio");
        return new ResultadoElegibilidadeMatching(false, criterioViolado, List.of());
    }
}

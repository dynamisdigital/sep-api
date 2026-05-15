package com.dynamis.sep_api.onboarding.application.port.out.dto;

import com.dynamis.sep_api.onboarding.domain.vo.BasePld;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Resposta consolidada de uma consulta PLD. {@code hits} agrupa todos os hits individuais por
 * base; quando vazio, o alvo passou limpo em todas as bases que o provider respondeu.
 *
 * <p>{@code basesConsultadas} carrega o conjunto de bases efetivamente respondidas pelo provider
 * (hit ou limpo). Caller obriga {@code basesConsultadas.containsAll(BASES_OBRIGATORIAS)} antes de
 * consolidar — resposta parcial nao pode fabricar "limpo" para bases ausentes (LGPD/regulatorio).
 *
 * <p>{@code payloadProvider} carrega o payload bruto consolidado para trilha auditavel
 * (CMN 4.656/2018 + LGPD Art. 16, retencao 5 anos).
 */
public record RespostaPld(List<HitPld> hits, Set<BasePld> basesConsultadas, String payloadProvider) {

    public RespostaPld {
        hits = hits == null ? List.of() : List.copyOf(hits);
        basesConsultadas = basesConsultadas == null
                ? Collections.unmodifiableSet(EnumSet.noneOf(BasePld.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(basesConsultadas));
    }

    public boolean limpo() {
        return hits.isEmpty();
    }
}

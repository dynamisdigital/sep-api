package com.dynamis.sep_api.onboarding.application.port.out.dto;

import java.util.List;

/**
 * Resposta consolidada de uma consulta PLD. {@code hits} agrupa todos os hits individuais por
 * base; quando vazio, o alvo passou limpo em todas as bases consultadas. {@code limpo} reflete
 * exatamente {@code hits.isEmpty()} — qualquer hit em qualquer base bloqueia onboarding.
 *
 * <p>{@code payloadProvider} carrega o payload bruto consolidado para trilha auditavel
 * (CMN 4.656/2018 + LGPD Art. 16, retencao 5 anos).
 */
public record RespostaPld(List<HitPld> hits, String payloadProvider) {

    public boolean limpo() {
        return hits == null || hits.isEmpty();
    }
}

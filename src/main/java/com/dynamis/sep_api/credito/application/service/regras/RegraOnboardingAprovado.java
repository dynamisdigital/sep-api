package com.dynamis.sep_api.credito.application.service.regras;

import com.dynamis.sep_api.credito.application.service.RegraCredito;
import com.dynamis.sep_api.credito.application.service.dto.ContextoAvaliacaoCredito;
import com.dynamis.sep_api.credito.application.service.dto.RegraResultado;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import org.springframework.stereotype.Component;

/**
 * Pre-requisito absoluto: onboarding deve estar em {@link StatusOnboarding#APROVADO_FINAL} (apos
 * KYC + PLD limpos — Sprints 6/7). Falha aqui e bloqueante: motor encerra e sugere {@code
 * REJEITADA} direto, independente do score (CMN 4.656/2018 — vedacao de credito sem KYC/PLD).
 */
@Component
public class RegraOnboardingAprovado implements RegraCredito {

    public static final String NOME = "onboarding-aprovado-final";

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public RegraResultado avaliar(ContextoAvaliacaoCredito contexto) {
        if (contexto.statusOnboarding() == StatusOnboarding.APROVADO_FINAL) {
            return RegraResultado.passou(NOME);
        }
        return RegraResultado.falhouBloqueante(
                NOME, "Onboarding deve estar APROVADO_FINAL; atual: " + contexto.statusOnboarding());
    }
}

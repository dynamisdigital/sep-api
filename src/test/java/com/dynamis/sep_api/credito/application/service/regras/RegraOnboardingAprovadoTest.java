package com.dynamis.sep_api.credito.application.service.regras;

import com.dynamis.sep_api.credito.application.service.MotorTestFixtures;
import com.dynamis.sep_api.credito.application.service.dto.ContextoAvaliacaoCredito;
import com.dynamis.sep_api.credito.application.service.dto.RegraResultado;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.ResultadoRegra;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RegraOnboardingAprovadoTest {

    private final RegraOnboardingAprovado regra = new RegraOnboardingAprovado();

    @Test
    void aprovadoFinalPassa() {
        PropostaCredito p = MotorTestFixtures.propostaPf(new BigDecimal("10000"), 12);
        RegraResultado r = regra.avaliar(MotorTestFixtures.contextoPfOk(p));
        assertThat(r.resultado()).isEqualTo(ResultadoRegra.PASSOU);
        assertThat(r.bloqueante()).isFalse();
    }

    @Test
    void naoAprovadoFalhaBloqueante() {
        PropostaCredito p = MotorTestFixtures.propostaPf(new BigDecimal("10000"), 12);
        ContextoAvaliacaoCredito c = new ContextoAvaliacaoCredito(
                p,
                TipoSolicitante.PESSOA,
                StatusOnboarding.APROVADO,
                LocalDate.now().minusYears(30),
                null);
        RegraResultado r = regra.avaliar(c);
        assertThat(r.resultado()).isEqualTo(ResultadoRegra.FALHOU);
        assertThat(r.bloqueante()).isTrue();
        assertThat(r.motivo()).contains("APROVADO_FINAL");
    }
}

package com.dynamis.sep_api.credito.application.service.regras;

import com.dynamis.sep_api.credito.application.service.CreditoMotorProperties;
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

class RegraIdadeMinimaPessoaTest {

    private final CreditoMotorProperties properties = MotorTestFixtures.propertiesDefault();
    private final RegraIdadeMinimaPessoa regra = new RegraIdadeMinimaPessoa(properties);

    @Test
    void pfMaiorDeIdadePassa() {
        PropostaCredito p = MotorTestFixtures.propostaPf(new BigDecimal("10000"), 12);
        RegraResultado r = regra.avaliar(MotorTestFixtures.contextoPfOk(p));
        assertThat(r.resultado()).isEqualTo(ResultadoRegra.PASSOU);
    }

    @Test
    void pfMenorDeIdadeFalha() {
        PropostaCredito p = MotorTestFixtures.propostaPf(new BigDecimal("10000"), 12);
        ContextoAvaliacaoCredito c = new ContextoAvaliacaoCredito(
                p,
                TipoSolicitante.PESSOA,
                StatusOnboarding.APROVADO_FINAL,
                LocalDate.now().minusYears(17),
                null);
        RegraResultado r = regra.avaliar(c);
        assertThat(r.resultado()).isEqualTo(ResultadoRegra.FALHOU);
        assertThat(r.bloqueante()).isFalse();
    }

    @Test
    void pjRetornaPassouNaoAplicavel() {
        PropostaCredito p = MotorTestFixtures.propostaPj(new BigDecimal("50000"), 12);
        RegraResultado r = regra.avaliar(MotorTestFixtures.contextoPjOk(p));
        assertThat(r.resultado()).isEqualTo(ResultadoRegra.PASSOU);
    }

    @Test
    void pfSemDataNascimentoPendente() {
        PropostaCredito p = MotorTestFixtures.propostaPf(new BigDecimal("10000"), 12);
        ContextoAvaliacaoCredito c =
                new ContextoAvaliacaoCredito(p, TipoSolicitante.PESSOA, StatusOnboarding.APROVADO_FINAL, null, null);
        RegraResultado r = regra.avaliar(c);
        assertThat(r.resultado()).isEqualTo(ResultadoRegra.PENDENTE);
    }
}

package com.dynamis.sep_api.credito.application.service.regras;

import com.dynamis.sep_api.credito.application.service.CreditoMotorProperties;
import com.dynamis.sep_api.credito.application.service.MotorTestFixtures;
import com.dynamis.sep_api.credito.application.service.dto.RegraResultado;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.ResultadoRegra;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RegraValorMaximoTest {

    private final CreditoMotorProperties properties = MotorTestFixtures.propertiesDefault();
    private final RegraValorMaximo regra = new RegraValorMaximo(properties);

    @Test
    void pfDentroDoLimitePassa() {
        PropostaCredito p = MotorTestFixtures.propostaPf(new BigDecimal("50000.00"), 12);
        assertThat(regra.avaliar(MotorTestFixtures.contextoPfOk(p)).resultado()).isEqualTo(ResultadoRegra.PASSOU);
    }

    @Test
    void pfAcimaDoLimiteFalha() {
        PropostaCredito p = MotorTestFixtures.propostaPf(new BigDecimal("50001"), 12);
        RegraResultado r = regra.avaliar(MotorTestFixtures.contextoPfOk(p));
        assertThat(r.resultado()).isEqualTo(ResultadoRegra.FALHOU);
    }

    @Test
    void pjDentroDoLimitePassa() {
        PropostaCredito p = MotorTestFixtures.propostaPj(new BigDecimal("200000"), 24);
        assertThat(regra.avaliar(MotorTestFixtures.contextoPjOk(p)).resultado()).isEqualTo(ResultadoRegra.PASSOU);
    }

    @Test
    void pjAcimaDoLimiteFalha() {
        PropostaCredito p = MotorTestFixtures.propostaPj(new BigDecimal("200001"), 24);
        assertThat(regra.avaliar(MotorTestFixtures.contextoPjOk(p)).resultado()).isEqualTo(ResultadoRegra.FALHOU);
    }
}

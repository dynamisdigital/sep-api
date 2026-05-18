package com.dynamis.sep_api.credito.application.service.regras;

import com.dynamis.sep_api.credito.application.service.CreditoMotorProperties;
import com.dynamis.sep_api.credito.application.service.MotorTestFixtures;
import com.dynamis.sep_api.credito.application.service.dto.RegraResultado;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.ResultadoRegra;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RegraPrazoMaximoTest {

    private final CreditoMotorProperties properties = MotorTestFixtures.propertiesDefault();
    private final RegraPrazoMaximo regra = new RegraPrazoMaximo(properties);

    @Test
    void pfDentroDoLimitePassa() {
        PropostaCredito p = MotorTestFixtures.propostaPf(new BigDecimal("10000"), 12);
        assertThat(regra.avaliar(MotorTestFixtures.contextoPfOk(p)).resultado()).isEqualTo(ResultadoRegra.PASSOU);
    }

    @Test
    void pfAcimaDoLimiteFalha() {
        PropostaCredito p = MotorTestFixtures.propostaPf(new BigDecimal("10000"), 13);
        RegraResultado r = regra.avaliar(MotorTestFixtures.contextoPfOk(p));
        assertThat(r.resultado()).isEqualTo(ResultadoRegra.FALHOU);
    }

    @Test
    void pjDentroDoLimitePassa() {
        PropostaCredito p = MotorTestFixtures.propostaPj(new BigDecimal("50000"), 24);
        assertThat(regra.avaliar(MotorTestFixtures.contextoPjOk(p)).resultado()).isEqualTo(ResultadoRegra.PASSOU);
    }

    @Test
    void pjAcimaDoLimiteFalha() {
        PropostaCredito p = MotorTestFixtures.propostaPj(new BigDecimal("50000"), 25);
        assertThat(regra.avaliar(MotorTestFixtures.contextoPjOk(p)).resultado()).isEqualTo(ResultadoRegra.FALHOU);
    }
}

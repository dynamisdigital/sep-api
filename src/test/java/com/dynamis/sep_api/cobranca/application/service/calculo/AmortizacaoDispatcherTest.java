package com.dynamis.sep_api.cobranca.application.service.calculo;

import com.dynamis.sep_api.cobranca.application.service.calculo.dto.ParametrosCalculo;
import com.dynamis.sep_api.cobranca.application.service.calculo.dto.ResultadoCalculo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmortizacaoDispatcherTest {

    @Test
    void roteia_paraPrice() {
        AmortizacaoDispatcher d = new AmortizacaoDispatcher(List.of(new CalculadoraPrice(), new CalculadoraSAC()));

        ResultadoCalculo r = d.calcular(new ParametrosCalculo(
                new BigDecimal("1200"),
                BigDecimal.ZERO,
                12,
                LocalDate.of(2026, 1, 1),
                SistemaAmortizacao.PRICE,
                30,
                30));

        assertThat(r.parcelas()).hasSize(12);
    }

    @Test
    void roteia_paraSac() {
        AmortizacaoDispatcher d = new AmortizacaoDispatcher(List.of(new CalculadoraPrice(), new CalculadoraSAC()));

        ResultadoCalculo r = d.calcular(new ParametrosCalculo(
                new BigDecimal("1200"), BigDecimal.ZERO, 12, LocalDate.of(2026, 1, 1), SistemaAmortizacao.SAC, 30, 30));

        assertThat(r.parcelas()).hasSize(12);
    }

    @Test
    void duasCalculadorasMesmoSistema_falhaNaConstrucao() {
        assertThatThrownBy(() -> new AmortizacaoDispatcher(List.of(new CalculadoraPrice(), new CalculadoraPrice())))
                .isInstanceOf(IllegalStateException.class);
    }
}

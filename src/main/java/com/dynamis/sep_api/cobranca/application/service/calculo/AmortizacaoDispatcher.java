package com.dynamis.sep_api.cobranca.application.service.calculo;

import com.dynamis.sep_api.cobranca.application.service.calculo.dto.ParametrosCalculo;
import com.dynamis.sep_api.cobranca.application.service.calculo.dto.ResultadoCalculo;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Roteia {@link ParametrosCalculo} pra implementacao correta de {@link CalculadoraAmortizacao}.
 * Strategy + Map injection: Spring injeta todas as {@code CalculadoraAmortizacao} disponiveis no
 * contexto e o dispatcher indexa por {@link SistemaAmortizacao}.
 */
@Component
public class AmortizacaoDispatcher {

    private final Map<SistemaAmortizacao, CalculadoraAmortizacao> calculadoras;

    public AmortizacaoDispatcher(List<CalculadoraAmortizacao> calculadoras) {
        Objects.requireNonNull(calculadoras, "calculadoras obrigatoria");
        this.calculadoras = new EnumMap<>(SistemaAmortizacao.class);
        for (CalculadoraAmortizacao c : calculadoras) {
            Objects.requireNonNull(c, "calculadora nao pode ser null");
            if (this.calculadoras.put(c.sistema(), c) != null) {
                throw new IllegalStateException("Duas calculadoras registradas para o mesmo sistema: " + c.sistema());
            }
        }
    }

    public ResultadoCalculo calcular(ParametrosCalculo parametros) {
        CalculadoraAmortizacao calc = calculadoras.get(parametros.sistema());
        if (calc == null) {
            throw new IllegalArgumentException("Sistema de amortizacao nao suportado: " + parametros.sistema());
        }
        return calc.calcular(parametros);
    }
}

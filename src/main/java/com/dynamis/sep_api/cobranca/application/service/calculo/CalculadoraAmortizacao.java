package com.dynamis.sep_api.cobranca.application.service.calculo;

import com.dynamis.sep_api.cobranca.application.service.calculo.dto.ParametrosCalculo;
import com.dynamis.sep_api.cobranca.application.service.calculo.dto.ResultadoCalculo;

/**
 * Strategy (GoF) de amortizacao. Implementacoes Price/SAC (Sprint 12 Task 12.2) sao injetadas no
 * dispatcher pra desacoplar {@code GerarAgendaPagamentoUseCase} (Task 12.3) do calculo financeiro.
 *
 * <p>Contrato: parcelas ordenadas por {@code numero}, separadas em principal/juros/multa/encargos
 * (multa/encargos = 0 na geracao inicial), arredondamento {@code HALF_UP} escala 2, ajuste residual
 * na ultima parcela pra fechar a soma do principal com {@code valorFinanciado}.
 */
public interface CalculadoraAmortizacao {

    SistemaAmortizacao sistema();

    ResultadoCalculo calcular(ParametrosCalculo parametros);
}

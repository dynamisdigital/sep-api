package com.dynamis.sep_api.cobranca.application.service.calculo;

/**
 * Sistemas de amortizacao suportados pela Sprint 12 (Task 12.2). Default da sprint: {@link #PRICE}
 * conforme spec Task 12.0. {@link #SAC} entregue como capacidade alternativa pra contratos futuros.
 */
public enum SistemaAmortizacao {
    PRICE,
    SAC
}

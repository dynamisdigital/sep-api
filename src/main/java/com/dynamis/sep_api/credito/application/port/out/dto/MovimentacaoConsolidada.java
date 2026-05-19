package com.dynamis.sep_api.credito.application.port.out.dto;

import java.math.BigDecimal;

/**
 * Snapshot consolidado de movimentacao bancaria devolvido pelo {@code OpenFinanceProvider}. NUNCA
 * carrega extrato bruto transacional — apenas agregados usados pela {@code
 * RegraOpenFinanceMovimentacao} (Task 9.4).
 *
 * <p>O {@code payloadConsolidado} e o payload original ja sanitizado em JSON (sem dados
 * identificaveis de conta bancaria) — persistido em {@code movimentacao_open_finance.payload_consolidado}
 * para auditoria.
 */
public record MovimentacaoConsolidada(
        String payloadConsolidado,
        BigDecimal mediaEntradasMensal,
        BigDecimal mediaSaidasMensal,
        BigDecimal saldoMedio,
        Integer numeroMesesAvaliados) {}

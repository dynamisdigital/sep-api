package com.dynamis.sep_api.credores.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resumo agregado de cobranca de uma operacao financiada, exposto pelo modulo {@code cobranca} ao
 * {@code credores} (Sprint 17). Apenas numeros agregados — sem dados sensiveis do tomador.
 *
 * <p>{@code proximoVencimento} nulo quando nao ha parcela em aberto.
 */
public record CarteiraCobrancaResumo(
        int numeroParcelas,
        BigDecimal valorTotal,
        int parcelasPagas,
        int parcelasAtrasadas,
        BigDecimal totalRecebido,
        LocalDate proximoVencimento) {}

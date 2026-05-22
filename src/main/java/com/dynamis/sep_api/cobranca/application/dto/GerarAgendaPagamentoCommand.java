package com.dynamis.sep_api.cobranca.application.dto;

import com.dynamis.sep_api.cobranca.application.service.calculo.SistemaAmortizacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Comando do {@code GerarAgendaPagamentoUseCase} (Sprint 12 Task 12.3). Toda a informacao
 * financeira da agenda vem em formato estruturado — o use case nao infere taxa, prazo ou valor de
 * texto de contrato.
 *
 * <p>{@code dataBase} eh tipicamente a {@code dataAssinatura} do contrato; primeira parcela vence
 * em 30 dias corridos a partir dela.
 */
public record GerarAgendaPagamentoCommand(
        UUID contratoId,
        UUID propostaId,
        UUID tomadorId,
        BigDecimal valorFinanciado,
        int numeroParcelas,
        BigDecimal taxaMensal,
        LocalDate dataBase,
        SistemaAmortizacao sistema) {

    public GerarAgendaPagamentoCommand {
        Objects.requireNonNull(contratoId, "contratoId obrigatorio");
        Objects.requireNonNull(propostaId, "propostaId obrigatorio");
        Objects.requireNonNull(tomadorId, "tomadorId obrigatorio");
        Objects.requireNonNull(valorFinanciado, "valorFinanciado obrigatorio");
        Objects.requireNonNull(taxaMensal, "taxaMensal obrigatoria");
        Objects.requireNonNull(dataBase, "dataBase obrigatoria");
        Objects.requireNonNull(sistema, "sistema obrigatorio");
        if (valorFinanciado.signum() <= 0) {
            throw new IllegalArgumentException("valorFinanciado deve ser positivo");
        }
        if (taxaMensal.signum() < 0) {
            throw new IllegalArgumentException("taxaMensal nao pode ser negativa");
        }
        if (numeroParcelas <= 0) {
            throw new IllegalArgumentException("numeroParcelas deve ser positivo");
        }
    }
}

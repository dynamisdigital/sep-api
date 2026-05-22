package com.dynamis.sep_api.cobranca.application.dto;

import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Snapshot imutavel da composicao atualizada de uma {@code ParcelaCobranca} no instante {@code
 * agora} (Sprint 12 Task 12.5). Resultado de {@code CalcularValorAtualizadoParcelaUseCase}: combina
 * a composicao original persistida com juros de mora e multa calculados em tempo de leitura,
 * abatendo o total ja recebido.
 *
 * <p>Todos os campos {@link BigDecimal} sao normalizados pra escala 2 ({@code HALF_UP}). Valores
 * negativos sao rejeitados — o use case ja clampa em zero antes de instanciar.
 *
 * <p>{@code valorDevidoAtualizado} eh o total BRUTO (composicaoOriginal.total + jurosMora + multa)
 * — comparar contra {@code totalRecebido} pra decidir status; {@code valorEmAberto} eh o saldo
 * remanescente clampado em zero ({@code valorDevidoAtualizado - totalRecebido}).
 */
public record ParcelaAtualizadaResult(
        UUID parcelaId,
        int numero,
        StatusParcela status,
        LocalDate dataVencimento,
        ComposicaoValor composicaoOriginal,
        BigDecimal jurosMora,
        BigDecimal multa,
        BigDecimal valorDevidoAtualizado,
        BigDecimal totalRecebido,
        BigDecimal valorEmAberto) {

    public ParcelaAtualizadaResult {
        Objects.requireNonNull(parcelaId, "parcelaId obrigatorio");
        Objects.requireNonNull(status, "status obrigatorio");
        Objects.requireNonNull(dataVencimento, "dataVencimento obrigatoria");
        Objects.requireNonNull(composicaoOriginal, "composicaoOriginal obrigatoria");
        jurosMora = normalizar("jurosMora", jurosMora);
        multa = normalizar("multa", multa);
        valorDevidoAtualizado = normalizar("valorDevidoAtualizado", valorDevidoAtualizado);
        totalRecebido = normalizar("totalRecebido", totalRecebido);
        valorEmAberto = normalizar("valorEmAberto", valorEmAberto);
    }

    private static BigDecimal normalizar(String campo, BigDecimal valor) {
        Objects.requireNonNull(valor, campo + " obrigatorio");
        if (valor.signum() < 0) {
            throw new IllegalArgumentException(campo + " nao pode ser negativo");
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}

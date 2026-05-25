package com.dynamis.sep_api.cobranca.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrada do {@code IniciarRenegociacaoUseCase} (Sprint 13 Task 13.6).
 *
 * <p>Validacoes profundas (valores, datas, regras de negocio) ficam no agregado
 * {@link com.dynamis.sep_api.cobranca.domain.model.Renegociacao#propor}; este record valida
 * apenas presenca dos campos obrigatorios.
 */
public record IniciarRenegociacaoCommand(
        UUID parcelaId,
        BigDecimal novoValorParcela,
        LocalDate novoVencimento,
        int numeroParcelas,
        BigDecimal desconto,
        String justificativa,
        UUID propostaPor) {

    public IniciarRenegociacaoCommand {
        Objects.requireNonNull(parcelaId, "parcelaId obrigatorio");
        Objects.requireNonNull(novoValorParcela, "novoValorParcela obrigatorio");
        Objects.requireNonNull(novoVencimento, "novoVencimento obrigatorio");
        Objects.requireNonNull(desconto, "desconto obrigatorio");
        Objects.requireNonNull(justificativa, "justificativa obrigatorio");
        Objects.requireNonNull(propostaPor, "propostaPor obrigatorio");
    }
}

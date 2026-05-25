package com.dynamis.sep_api.cobranca.web.dto;

import com.dynamis.sep_api.cobranca.application.dto.IniciarRenegociacaoCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload pra propor renegociacao (Sprint 13 Task 13.7). {@code FINANCEIRO}/{@code ADMIN} com
 * step-up.
 */
@Schema(description = "Proposta de renegociacao de uma parcela atrasada/inadimplente.")
public record IniciarRenegociacaoRequest(
        @NotNull @DecimalMin(value = "0.01", inclusive = true) @Schema(description = "Novo valor por parcela.")
                BigDecimal novoValorParcela,
        @NotNull @Schema(description = "Vencimento inicial da nova agenda.", example = "2026-07-10")
                LocalDate novoVencimento,
        @Min(1) @Schema(description = "Numero de parcelas substitutas (>= 1).") int numeroParcelas,
        @NotNull @PositiveOrZero @Schema(description = "Desconto sobre o valor original (>= 0).") BigDecimal desconto,
        @NotBlank @Size(max = 1000) @Schema(description = "Justificativa da renegociacao.") String justificativa) {

    public IniciarRenegociacaoCommand toCommand(UUID parcelaId, UUID propostaPor) {
        return new IniciarRenegociacaoCommand(
                parcelaId, novoValorParcela, novoVencimento, numeroParcelas, desconto, justificativa, propostaPor);
    }
}

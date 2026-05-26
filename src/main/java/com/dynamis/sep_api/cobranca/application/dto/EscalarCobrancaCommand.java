package com.dynamis.sep_api.cobranca.application.dto;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrada do {@code EscalarCobrancaUseCase} (Sprint 13 Task 13.4).
 *
 * <p>Destinatarios sao opcionais — se {@code null}, o use case grava evento de FALHA com motivo
 * {@code "destinatario indisponivel"} e segue para o proximo canal da etapa. Variaveis
 * referenciadas pelos templates (ex. {@code numeroParcela}, {@code dataVencimento}) devem ser
 * fornecidas pelo caller (listener/job) ja sanitizadas.
 */
public record EscalarCobrancaCommand(
        UUID parcelaId,
        int diasAtraso,
        String emailTomador,
        String telefoneTomador,
        Map<String, Object> variaveis,
        String correlationId) {

    public EscalarCobrancaCommand {
        Objects.requireNonNull(parcelaId, "parcelaId obrigatorio");
        if (diasAtraso < 0) {
            throw new IllegalArgumentException("diasAtraso nao pode ser negativo: " + diasAtraso);
        }
        Objects.requireNonNull(variaveis, "variaveis obrigatorio (use Map.of() se vazio)");
        variaveis = Map.copyOf(variaveis);
    }
}

package com.dynamis.sep_api.credito.application.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Thresholds e pesos do {@code MotorRegrasCredito} (Sprint 8 Task 8.2). Configuravel via
 * {@code app.credito.motor.*} no {@code application.yml} — permite ajuste sem mudanca de codigo
 * (ADR 0011).
 */
@ConfigurationProperties(prefix = "app.credito.motor")
public record CreditoMotorProperties(
        int scoreInicial,
        int penalidadeFalha,
        int penalidadePendencia,
        int scorePreAprovacao,
        int scoreAnalise,
        int idadeMinimaPessoa,
        int tempoMinimoEmpresaMeses,
        BigDecimal valorMaximoPf,
        BigDecimal valorMaximoPj,
        int prazoMaximoPfMeses,
        int prazoMaximoPjMeses) {

    public CreditoMotorProperties {
        if (scoreInicial <= 0 || scoreInicial > 1000) {
            throw new IllegalArgumentException("scoreInicial deve estar entre 1 e 1000");
        }
        if (penalidadeFalha < 0 || penalidadePendencia < 0) {
            throw new IllegalArgumentException("penalidades nao podem ser negativas");
        }
        if (scorePreAprovacao < scoreAnalise) {
            throw new IllegalArgumentException("scorePreAprovacao deve ser >= scoreAnalise");
        }
    }
}

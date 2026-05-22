package com.dynamis.sep_api.cobranca.application.service.calculo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Parametros financeiros default do modulo cobranca (Sprint 12 Task 12.2.3). Sobrescritos em
 * {@code application-*.yml} sob {@code app.cobranca}.
 *
 * <p>Defaults conforme spec Task 12.0.5 + Task 12.2.3:
 *
 * <ul>
 *   <li>{@code amortizacao-default = PRICE}
 *   <li>{@code primeira-parcela-dias = 30}
 *   <li>{@code periodicidade-dias = 30}
 *   <li>{@code juros-mora-mensal = 0.01} (1% ao mes pro rata die)
 *   <li>{@code multa-atraso = 0.02} (2% one-shot — limite CDC §52)
 *   <li>{@code job-atraso-cron = "0 0 2 * * *"} (diario as 02:00 America/Sao_Paulo)
 * </ul>
 */
@Configuration
@ConfigurationProperties(prefix = "app.cobranca")
public class ParametrosCobrancaProperties {

    private SistemaAmortizacao amortizacaoDefault = SistemaAmortizacao.PRICE;
    private int primeiraParcelaDias = 30;
    private int periodicidadeDias = 30;
    private BigDecimal jurosMoraMensal = new BigDecimal("0.01");
    private BigDecimal multaAtraso = new BigDecimal("0.02");
    private String jobAtrasoCron = "0 0 2 * * *";

    public SistemaAmortizacao getAmortizacaoDefault() {
        return amortizacaoDefault;
    }

    public void setAmortizacaoDefault(SistemaAmortizacao amortizacaoDefault) {
        this.amortizacaoDefault = amortizacaoDefault;
    }

    public int getPrimeiraParcelaDias() {
        return primeiraParcelaDias;
    }

    public void setPrimeiraParcelaDias(int primeiraParcelaDias) {
        this.primeiraParcelaDias = primeiraParcelaDias;
    }

    public int getPeriodicidadeDias() {
        return periodicidadeDias;
    }

    public void setPeriodicidadeDias(int periodicidadeDias) {
        this.periodicidadeDias = periodicidadeDias;
    }

    public BigDecimal getJurosMoraMensal() {
        return jurosMoraMensal;
    }

    public void setJurosMoraMensal(BigDecimal jurosMoraMensal) {
        this.jurosMoraMensal = jurosMoraMensal;
    }

    public BigDecimal getMultaAtraso() {
        return multaAtraso;
    }

    public void setMultaAtraso(BigDecimal multaAtraso) {
        this.multaAtraso = multaAtraso;
    }

    public String getJobAtrasoCron() {
        return jobAtrasoCron;
    }

    public void setJobAtrasoCron(String jobAtrasoCron) {
        this.jobAtrasoCron = jobAtrasoCron;
    }
}

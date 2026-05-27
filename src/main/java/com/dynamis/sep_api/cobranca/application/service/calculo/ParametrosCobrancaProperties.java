package com.dynamis.sep_api.cobranca.application.service.calculo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

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
@Validated
public class ParametrosCobrancaProperties {

    @NotNull
    private SistemaAmortizacao amortizacaoDefault = SistemaAmortizacao.PRICE;

    @Min(1)
    private int primeiraParcelaDias = 30;

    @Min(1)
    private int periodicidadeDias = 30;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal jurosMoraMensal = new BigDecimal("0.01");

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal multaAtraso = new BigDecimal("0.02");

    /**
     * Taxa de juros remuneratorios mensal default da operacao quando a proposta nao persistir taxa
     * propria. Placeholder Sprint 12 ate Sprint posterior (ou Epic 16) adicionar coluna {@code
     * taxa_juros_mensal} em {@code proposta_credito}/{@code decisao_credito}. 2% am eh ordem de
     * grandeza tipica de capital de giro PJ.
     */
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal taxaJurosMensalDefault = new BigDecimal("0.02");

    @NotBlank
    private String jobAtrasoCron = "0 0 2 * * *";

    /**
     * Dias de atraso a partir dos quais uma parcela transita de {@code ATRASADA} para
     * {@code INADIMPLENTE} (Sprint 13 / 15F-006). 90 dias eh referencia BACEN para inadimplencia
     * de credito; sobrescrever em ambientes de teste pra acelerar fluxos.
     */
    @Min(1)
    private int diasInadimplencia = 90;

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

    public BigDecimal getTaxaJurosMensalDefault() {
        return taxaJurosMensalDefault;
    }

    public void setTaxaJurosMensalDefault(BigDecimal taxaJurosMensalDefault) {
        this.taxaJurosMensalDefault = taxaJurosMensalDefault;
    }

    public String getJobAtrasoCron() {
        return jobAtrasoCron;
    }

    public void setJobAtrasoCron(String jobAtrasoCron) {
        this.jobAtrasoCron = jobAtrasoCron;
    }

    public int getDiasInadimplencia() {
        return diasInadimplencia;
    }

    public void setDiasInadimplencia(int diasInadimplencia) {
        this.diasInadimplencia = diasInadimplencia;
    }
}

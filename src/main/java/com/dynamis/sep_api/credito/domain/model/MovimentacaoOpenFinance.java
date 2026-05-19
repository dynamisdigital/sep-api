package com.dynamis.sep_api.credito.domain.model;

import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Snapshot consolidado de movimentacao bancaria recebido via Open Finance (Sprint 9). Persistido
 * apenas com dados agregados necessarios ao motor de credito — NUNCA extrato bruto transacional.
 *
 * <p>LGPD: o payload Celcoin original e armazenado em {@code payload_consolidado} ja sanitizado
 * (sem dados identificaveis de contas bancarias) e tem retencao limitada documentada em
 * {@code OPEN-FINANCE.md}.
 */
@Entity
@Table(name = "movimentacao_open_finance")
public class MovimentacaoOpenFinance {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "consentimento_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID consentimentoId;

    @Column(name = "proposta_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID propostaId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_consolidado", columnDefinition = "jsonb", nullable = false)
    private String payloadConsolidado;

    @Column(name = "media_entradas_mensal", precision = 15, scale = 2)
    private BigDecimal mediaEntradasMensal;

    @Column(name = "media_saidas_mensal", precision = 15, scale = 2)
    private BigDecimal mediaSaidasMensal;

    @Column(name = "saldo_medio", precision = 15, scale = 2)
    private BigDecimal saldoMedio;

    @Column(name = "numero_meses_avaliados")
    private Integer numeroMesesAvaliados;

    @Column(name = "data_recebimento", nullable = false)
    private OffsetDateTime dataRecebimento;

    protected MovimentacaoOpenFinance() {
        // Hibernate
    }

    private MovimentacaoOpenFinance(
            UUID id,
            UUID consentimentoId,
            UUID propostaId,
            String payloadConsolidado,
            BigDecimal mediaEntradasMensal,
            BigDecimal mediaSaidasMensal,
            BigDecimal saldoMedio,
            Integer numeroMesesAvaliados) {
        this.id = id;
        this.consentimentoId = consentimentoId;
        this.propostaId = propostaId;
        this.payloadConsolidado = payloadConsolidado;
        this.mediaEntradasMensal = mediaEntradasMensal;
        this.mediaSaidasMensal = mediaSaidasMensal;
        this.saldoMedio = saldoMedio;
        this.numeroMesesAvaliados = numeroMesesAvaliados;
        this.dataRecebimento = OffsetDateTime.now();
    }

    public static MovimentacaoOpenFinance registrar(
            UUID consentimentoId,
            UUID propostaId,
            String payloadConsolidado,
            BigDecimal mediaEntradasMensal,
            BigDecimal mediaSaidasMensal,
            BigDecimal saldoMedio,
            Integer numeroMesesAvaliados) {
        Objects.requireNonNull(consentimentoId, "consentimentoId obrigatorio");
        Objects.requireNonNull(propostaId, "propostaId obrigatorio");
        Objects.requireNonNull(payloadConsolidado, "payloadConsolidado obrigatorio");
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new MovimentacaoOpenFinance(
                id,
                consentimentoId,
                propostaId,
                payloadConsolidado,
                mediaEntradasMensal,
                mediaSaidasMensal,
                saldoMedio,
                numeroMesesAvaliados);
    }

    public UUID getId() {
        return id;
    }

    public UUID getConsentimentoId() {
        return consentimentoId;
    }

    public UUID getPropostaId() {
        return propostaId;
    }

    public String getPayloadConsolidado() {
        return payloadConsolidado;
    }

    public BigDecimal getMediaEntradasMensal() {
        return mediaEntradasMensal;
    }

    public BigDecimal getMediaSaidasMensal() {
        return mediaSaidasMensal;
    }

    public BigDecimal getSaldoMedio() {
        return saldoMedio;
    }

    public Integer getNumeroMesesAvaliados() {
        return numeroMesesAvaliados;
    }

    public OffsetDateTime getDataRecebimento() {
        return dataRecebimento;
    }
}

package com.dynamis.sep_api.cobranca.domain.model;

import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;
import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Renegociacao basica de uma parcela atrasada/inadimplente (Sprint 13 Task 13.2).
 *
 * <p>Nasce em {@code PROPOSTA} pelo financeiro com novas condicoes (valor, vencimento, numero de
 * parcelas, desconto opcional, justificativa). Tomador aceita (gera nova {@code AgendaPagamento}
 * substituta em Task 13.6) ou recusa (parcela volta ao status anterior). Sem decisao em 7 dias,
 * job expira (Task 13.6).
 *
 * <p>Apenas uma renegociacao ativa por parcela — garantido por unique parcial na migration ({@code
 * uq_renegociacao_parcela_ativa}).
 */
@Entity
@Table(name = "renegociacao")
public class Renegociacao extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "parcela_original_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID parcelaOriginalId;

    @Column(name = "agenda_original_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID agendaOriginalId;

    @Column(name = "tomador_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID tomadorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusRenegociacao status;

    /** Status da parcela antes da entrada em EM_NEGOCIACAO — pra reverter em recusa/expiracao. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_parcela_anterior", nullable = false, length = 20, updatable = false)
    private StatusParcela statusParcelaAnterior;

    @Column(name = "novo_valor_parcela", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal novoValorParcela;

    @Column(name = "novo_vencimento", nullable = false, updatable = false)
    private LocalDate novoVencimento;

    @Column(name = "numero_parcelas", nullable = false, updatable = false)
    private int numeroParcelas;

    @Column(name = "desconto", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal desconto;

    @Column(name = "justificativa", nullable = false, length = 1000, updatable = false)
    private String justificativa;

    @Column(name = "proposta_por", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID propostaPor;

    @Column(name = "data_proposta", nullable = false, updatable = false)
    private OffsetDateTime dataProposta;

    @Column(name = "data_expiracao", nullable = false, updatable = false)
    private OffsetDateTime dataExpiracao;

    @Column(name = "data_decisao")
    private OffsetDateTime dataDecisao;

    @Column(name = "agenda_substituta_id", columnDefinition = "uuid")
    private UUID agendaSubstitutaId;

    protected Renegociacao() {}

    private Renegociacao(
            UUID id,
            UUID parcelaOriginalId,
            UUID agendaOriginalId,
            UUID tomadorId,
            StatusParcela statusParcelaAnterior,
            BigDecimal novoValorParcela,
            LocalDate novoVencimento,
            int numeroParcelas,
            BigDecimal desconto,
            String justificativa,
            UUID propostaPor,
            OffsetDateTime dataProposta,
            OffsetDateTime dataExpiracao) {
        this.id = id;
        this.parcelaOriginalId = parcelaOriginalId;
        this.agendaOriginalId = agendaOriginalId;
        this.tomadorId = tomadorId;
        this.status = StatusRenegociacao.PROPOSTA;
        this.statusParcelaAnterior = statusParcelaAnterior;
        this.novoValorParcela = novoValorParcela;
        this.novoVencimento = novoVencimento;
        this.numeroParcelas = numeroParcelas;
        this.desconto = desconto;
        this.justificativa = justificativa;
        this.propostaPor = propostaPor;
        this.dataProposta = dataProposta;
        this.dataExpiracao = dataExpiracao;
    }

    public static Renegociacao propor(
            UUID parcelaOriginalId,
            UUID agendaOriginalId,
            UUID tomadorId,
            StatusParcela statusParcelaAnterior,
            BigDecimal novoValorParcela,
            LocalDate novoVencimento,
            int numeroParcelas,
            BigDecimal desconto,
            String justificativa,
            UUID propostaPor,
            OffsetDateTime dataProposta,
            OffsetDateTime dataExpiracao) {
        Objects.requireNonNull(parcelaOriginalId, "parcelaOriginalId obrigatorio");
        Objects.requireNonNull(agendaOriginalId, "agendaOriginalId obrigatorio");
        Objects.requireNonNull(tomadorId, "tomadorId obrigatorio");
        Objects.requireNonNull(statusParcelaAnterior, "statusParcelaAnterior obrigatorio");
        if (statusParcelaAnterior != StatusParcela.ATRASADA && statusParcelaAnterior != StatusParcela.INADIMPLENTE) {
            throw new IllegalArgumentException("statusParcelaAnterior invalido: " + statusParcelaAnterior
                    + " (esperado ATRASADA ou INADIMPLENTE)");
        }
        Objects.requireNonNull(novoValorParcela, "novoValorParcela obrigatorio");
        if (novoValorParcela.signum() <= 0) {
            throw new IllegalArgumentException("novoValorParcela deve ser positivo");
        }
        Objects.requireNonNull(novoVencimento, "novoVencimento obrigatorio");
        if (numeroParcelas <= 0) {
            throw new IllegalArgumentException("numeroParcelas deve ser positivo");
        }
        Objects.requireNonNull(desconto, "desconto obrigatorio");
        if (desconto.signum() < 0) {
            throw new IllegalArgumentException("desconto nao pode ser negativo");
        }
        if (justificativa == null || justificativa.isBlank()) {
            throw new IllegalArgumentException("justificativa obrigatoria");
        }
        Objects.requireNonNull(propostaPor, "propostaPor obrigatorio");
        Objects.requireNonNull(dataProposta, "dataProposta obrigatoria");
        Objects.requireNonNull(dataExpiracao, "dataExpiracao obrigatoria");
        if (!dataExpiracao.isAfter(dataProposta)) {
            throw new IllegalArgumentException("dataExpiracao deve ser posterior a dataProposta");
        }
        return new Renegociacao(
                Generators.timeBasedReorderedGenerator().generate(),
                parcelaOriginalId,
                agendaOriginalId,
                tomadorId,
                statusParcelaAnterior,
                novoValorParcela,
                novoVencimento,
                numeroParcelas,
                desconto,
                justificativa,
                propostaPor,
                dataProposta,
                dataExpiracao);
    }

    public void aceitar(UUID agendaSubstitutaId, OffsetDateTime dataDecisao) {
        Objects.requireNonNull(agendaSubstitutaId, "agendaSubstitutaId obrigatorio");
        Objects.requireNonNull(dataDecisao, "dataDecisao obrigatoria");
        exigirEmProposta("aceitar");
        this.status = StatusRenegociacao.ACEITA;
        this.agendaSubstitutaId = agendaSubstitutaId;
        this.dataDecisao = dataDecisao;
    }

    public void recusar(OffsetDateTime dataDecisao) {
        Objects.requireNonNull(dataDecisao, "dataDecisao obrigatoria");
        exigirEmProposta("recusar");
        this.status = StatusRenegociacao.RECUSADA;
        this.dataDecisao = dataDecisao;
    }

    public void expirar(OffsetDateTime dataDecisao) {
        Objects.requireNonNull(dataDecisao, "dataDecisao obrigatoria");
        exigirEmProposta("expirar");
        this.status = StatusRenegociacao.EXPIRADA;
        this.dataDecisao = dataDecisao;
    }

    private void exigirEmProposta(String acao) {
        if (this.status != StatusRenegociacao.PROPOSTA) {
            throw new IllegalStateException("renegociacao em status " + this.status + " nao aceita acao " + acao);
        }
    }

    public boolean expirouEm(OffsetDateTime agora) {
        return this.status == StatusRenegociacao.PROPOSTA && !agora.isBefore(this.dataExpiracao);
    }

    public UUID getId() {
        return id;
    }

    public UUID getParcelaOriginalId() {
        return parcelaOriginalId;
    }

    public UUID getAgendaOriginalId() {
        return agendaOriginalId;
    }

    public UUID getTomadorId() {
        return tomadorId;
    }

    public StatusRenegociacao getStatus() {
        return status;
    }

    public StatusParcela getStatusParcelaAnterior() {
        return statusParcelaAnterior;
    }

    public BigDecimal getNovoValorParcela() {
        return novoValorParcela;
    }

    public LocalDate getNovoVencimento() {
        return novoVencimento;
    }

    public int getNumeroParcelas() {
        return numeroParcelas;
    }

    public BigDecimal getDesconto() {
        return desconto;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public UUID getPropostaPor() {
        return propostaPor;
    }

    public OffsetDateTime getDataProposta() {
        return dataProposta;
    }

    public OffsetDateTime getDataExpiracao() {
        return dataExpiracao;
    }

    public OffsetDateTime getDataDecisao() {
        return dataDecisao;
    }

    public UUID getAgendaSubstitutaId() {
        return agendaSubstitutaId;
    }
}

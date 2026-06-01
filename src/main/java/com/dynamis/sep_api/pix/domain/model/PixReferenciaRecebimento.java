package com.dynamis.sep_api.pix.domain.model;

import com.dynamis.sep_api.pix.domain.vo.StatusPixReferenciaRecebimento;
import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Referencia Pix de recebimento gerada pelo SEP para uma parcela (Sprint 21 — Epic 15). O {@code
 * txid} eh o identificador deterministico que correlaciona o webhook {@code RECEBIMENTO_PIX} de
 * volta a parcela — sem ele, nao ha baixa automatica.
 *
 * <p>Isolamento DDD: guarda apenas IDs (parcela/contrato/tomador) — nunca importa entidades de
 * {@code cobranca}. Minimizacao: nenhum dado bancario/pessoal alem do necessario.
 */
@Entity
@Table(name = "pix_referencia_recebimento")
public class PixReferenciaRecebimento extends EntidadeAuditavel {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "parcela_id", nullable = false)
    private UUID parcelaId;

    @Column(name = "contrato_id", nullable = false)
    private UUID contratoId;

    @Column(name = "tomador_id", nullable = false)
    private UUID tomadorId;

    @Column(name = "valor_esperado", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorEsperado;

    @Column(name = "txid", nullable = false, length = 140)
    private String txid;

    @Column(name = "provider_referencia_id", length = 140)
    private String providerReferenciaId;

    @Column(name = "codigo_copia_cola", length = 2000)
    private String codigoCopiaCola;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StatusPixReferenciaRecebimento status;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    protected PixReferenciaRecebimento() {
        // JPA
    }

    private PixReferenciaRecebimento(
            UUID id,
            UUID parcelaId,
            UUID contratoId,
            UUID tomadorId,
            BigDecimal valorEsperado,
            String txid,
            String correlationId) {
        this.id = id;
        this.parcelaId = parcelaId;
        this.contratoId = contratoId;
        this.tomadorId = tomadorId;
        this.valorEsperado = valorEsperado;
        this.txid = txid;
        this.status = StatusPixReferenciaRecebimento.ATIVA;
        this.correlationId = correlationId;
    }

    /** Cria uma referencia {@code ATIVA} para a parcela, com o {@code txid} de correlacao do SEP. */
    public static PixReferenciaRecebimento criar(
            UUID parcelaId,
            UUID contratoId,
            UUID tomadorId,
            BigDecimal valorEsperado,
            String txid,
            String correlationId) {
        Objects.requireNonNull(parcelaId, "parcelaId obrigatorio");
        Objects.requireNonNull(contratoId, "contratoId obrigatorio");
        Objects.requireNonNull(tomadorId, "tomadorId obrigatorio");
        Objects.requireNonNull(valorEsperado, "valorEsperado obrigatorio");
        if (valorEsperado.signum() <= 0) {
            throw new IllegalArgumentException("valorEsperado deve ser positivo");
        }
        if (valorEsperado.scale() > 2) {
            throw new IllegalArgumentException("valorEsperado nao pode ter mais de 2 casas decimais");
        }
        Objects.requireNonNull(txid, "txid obrigatorio");
        if (txid.isBlank()) {
            throw new IllegalArgumentException("txid nao pode ser vazio");
        }
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new PixReferenciaRecebimento(
                id, parcelaId, contratoId, tomadorId, valorEsperado.setScale(2), txid, correlationId);
    }

    /** Vincula dados do provider (id da cobranca/QR + copia-cola), quando o provider gera a cobranca. */
    public void vincularProvider(String providerReferenciaId, String codigoCopiaCola) {
        this.providerReferenciaId = providerReferenciaId;
        this.codigoCopiaCola = codigoCopiaCola;
    }

    /** {@code ATIVA -> PAGA} quando o Pix correspondente eh recebido e baixado. Terminal. */
    public void marcarPaga() {
        exigirAtiva("marcarPaga");
        this.status = StatusPixReferenciaRecebimento.PAGA;
    }

    /** {@code ATIVA -> DIVERGENTE} quando o pagamento recebido nao casa (valor/estado da parcela). */
    public void marcarDivergente() {
        exigirAtiva("marcarDivergente");
        this.status = StatusPixReferenciaRecebimento.DIVERGENTE;
    }

    /** {@code ATIVA -> EXPIRADA}. */
    public void marcarExpirada() {
        exigirAtiva("marcarExpirada");
        this.status = StatusPixReferenciaRecebimento.EXPIRADA;
    }

    /** {@code ATIVA -> CANCELADA}. */
    public void cancelar() {
        exigirAtiva("cancelar");
        this.status = StatusPixReferenciaRecebimento.CANCELADA;
    }

    private void exigirAtiva(String operacao) {
        if (this.status != StatusPixReferenciaRecebimento.ATIVA) {
            throw new IllegalStateException(operacao + " exige referencia ATIVA; estado atual: " + this.status);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getParcelaId() {
        return parcelaId;
    }

    public UUID getContratoId() {
        return contratoId;
    }

    public UUID getTomadorId() {
        return tomadorId;
    }

    public BigDecimal getValorEsperado() {
        return valorEsperado;
    }

    public String getTxid() {
        return txid;
    }

    public String getProviderReferenciaId() {
        return providerReferenciaId;
    }

    public String getCodigoCopiaCola() {
        return codigoCopiaCola;
    }

    public StatusPixReferenciaRecebimento getStatus() {
        return status;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}

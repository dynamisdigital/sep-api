package com.dynamis.sep_api.pix.domain.model;

import com.dynamis.sep_api.pix.domain.vo.StatusPixRecebimento;
import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrada Pix identificada por evento de provider (Epic 15 / Sprint 19 — foundation).
 *
 * <p>Nasce em {@link StatusPixRecebimento#RECEBIDO} e <strong>nao concilia parcela de cobranca</strong>
 * nesta sprint; a baixa automatica fica para Sprints 20/21. Dedup por {@code endToEndId} (id E2E do
 * arranjo Pix), unico quando presente (V45).
 */
@Entity
@Table(name = "pix_recebimento")
public class PixRecebimento extends EntidadeAuditavel {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "end_to_end_id", length = 100)
    private String endToEndId;

    @Column(name = "valor", nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StatusPixRecebimento status;

    @Column(name = "recebido_em", nullable = false)
    private OffsetDateTime recebidoEm;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    protected PixRecebimento() {
        // JPA
    }

    private PixRecebimento(
            UUID id, String endToEndId, BigDecimal valor, StatusPixRecebimento status, OffsetDateTime recebidoEm, String correlationId) {
        this.id = id;
        this.endToEndId = endToEndId;
        this.valor = valor;
        this.status = status;
        this.recebidoEm = recebidoEm;
        this.correlationId = correlationId;
    }

    /** Registra uma entrada Pix recebida via provider em {@link StatusPixRecebimento#RECEBIDO}. */
    public static PixRecebimento registrar(String endToEndId, BigDecimal valor, OffsetDateTime recebidoEm, String correlationId) {
        Objects.requireNonNull(valor, "valor obrigatorio");
        if (valor.signum() <= 0) {
            throw new IllegalArgumentException("valor deve ser positivo");
        }
        Objects.requireNonNull(recebidoEm, "recebidoEm obrigatorio");
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new PixRecebimento(
                id, endToEndId, valor.setScale(2, RoundingMode.HALF_UP), StatusPixRecebimento.RECEBIDO, recebidoEm, correlationId);
    }

    /** Marca que o recebimento entrou em processamento interno. */
    public void marcarEmProcessamento() {
        exigirEstado(StatusPixRecebimento.RECEBIDO);
        this.status = StatusPixRecebimento.EM_PROCESSAMENTO;
    }

    /** Marca o recebimento como conciliado (vinculo a parcela ocorre em sprint futura). */
    public void marcarConciliado() {
        exigirEstado(StatusPixRecebimento.RECEBIDO, StatusPixRecebimento.EM_PROCESSAMENTO);
        this.status = StatusPixRecebimento.CONCILIADO;
    }

    /** Marca o recebimento como nao identificado quando nao ha vinculo possivel. */
    public void marcarNaoIdentificado() {
        exigirEstado(StatusPixRecebimento.RECEBIDO, StatusPixRecebimento.EM_PROCESSAMENTO);
        this.status = StatusPixRecebimento.NAO_IDENTIFICADO;
    }

    /** Marca falha de processamento; o recebimento fica disponivel para reprocesso. */
    public void marcarFalhou() {
        exigirEstado(StatusPixRecebimento.RECEBIDO, StatusPixRecebimento.EM_PROCESSAMENTO);
        this.status = StatusPixRecebimento.FALHOU;
    }

    private void exigirEstado(StatusPixRecebimento... permitidos) {
        for (StatusPixRecebimento permitido : permitidos) {
            if (this.status == permitido) {
                return;
            }
        }
        throw new IllegalStateException("transicao invalida a partir de " + this.status);
    }

    public UUID getId() {
        return id;
    }

    public String getEndToEndId() {
        return endToEndId;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public StatusPixRecebimento getStatus() {
        return status;
    }

    public OffsetDateTime getRecebidoEm() {
        return recebidoEm;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}

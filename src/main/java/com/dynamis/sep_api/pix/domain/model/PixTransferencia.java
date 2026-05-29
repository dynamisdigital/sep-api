package com.dynamis.sep_api.pix.domain.model;

import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
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
import java.util.Objects;
import java.util.UUID;

/**
 * Intencao de saida Pix (Epic 15 / Sprint 19 — foundation). Modela o ciclo de vida de uma
 * transferencia, mas <strong>nao executa desembolso real</strong> nesta sprint: o comando ao
 * provider entra nas Sprints 20/21.
 *
 * <p>Idempotencia garantida por {@code idempotencyKey} unica (V45). {@code externalId} so e
 * preenchido quando o provider confirma a solicitacao. Dados bancarios sensiveis (chave destino)
 * sao deliberadamente omitidos da foundation por minimizacao de dados.
 */
@Entity
@Table(name = "pix_transferencia")
public class PixTransferencia extends EntidadeAuditavel {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StatusPixTransferencia status;

    @Column(name = "valor", nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(name = "descricao", length = 255)
    private String descricao;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    protected PixTransferencia() {
        // JPA
    }

    private PixTransferencia(
            UUID id, String idempotencyKey, StatusPixTransferencia status, BigDecimal valor, String descricao, String correlationId) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.valor = valor;
        this.descricao = descricao;
        this.correlationId = correlationId;
    }

    /** Cria uma transferencia em {@link StatusPixTransferencia#CRIADA}, antes de qualquer chamada ao provider. */
    public static PixTransferencia criar(BigDecimal valor, String descricao, String idempotencyKey, String correlationId) {
        Objects.requireNonNull(valor, "valor obrigatorio");
        if (valor.signum() <= 0) {
            throw new IllegalArgumentException("valor deve ser positivo");
        }
        Objects.requireNonNull(idempotencyKey, "idempotencyKey obrigatoria");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey nao pode ser vazia");
        }
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new PixTransferencia(
                id, idempotencyKey, StatusPixTransferencia.CRIADA, valor.setScale(2, RoundingMode.HALF_UP), descricao, correlationId);
    }

    /** Registra que o provider aceitou a solicitacao, guardando o id externo retornado. */
    public void marcarSolicitada(String externalId) {
        exigirEstado(StatusPixTransferencia.CRIADA);
        Objects.requireNonNull(externalId, "externalId obrigatorio ao solicitar");
        if (externalId.isBlank()) {
            throw new IllegalArgumentException("externalId nao pode ser vazio");
        }
        this.externalId = externalId;
        this.status = StatusPixTransferencia.SOLICITADA;
    }

    /** Marca que o provider iniciou o processamento da transferencia. */
    public void marcarProcessando() {
        exigirEstado(StatusPixTransferencia.SOLICITADA);
        this.status = StatusPixTransferencia.PROCESSANDO;
    }

    /** Conclui a transferencia apos confirmacao do provider. */
    public void marcarConcluida() {
        exigirEstado(StatusPixTransferencia.SOLICITADA, StatusPixTransferencia.PROCESSANDO);
        this.status = StatusPixTransferencia.CONCLUIDA;
    }

    /** Marca falha; permitida a partir de qualquer estado ativo (antes de conclusao/cancelamento). */
    public void marcarFalhou() {
        exigirEstado(StatusPixTransferencia.CRIADA, StatusPixTransferencia.SOLICITADA, StatusPixTransferencia.PROCESSANDO);
        this.status = StatusPixTransferencia.FALHOU;
    }

    /** Cancela uma transferencia ainda nao solicitada ao provider. */
    public void cancelar() {
        exigirEstado(StatusPixTransferencia.CRIADA);
        this.status = StatusPixTransferencia.CANCELADA;
    }

    private void exigirEstado(StatusPixTransferencia... permitidos) {
        for (StatusPixTransferencia permitido : permitidos) {
            if (this.status == permitido) {
                return;
            }
        }
        throw new IllegalStateException("transicao invalida a partir de " + this.status);
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public StatusPixTransferencia getStatus() {
        return status;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}

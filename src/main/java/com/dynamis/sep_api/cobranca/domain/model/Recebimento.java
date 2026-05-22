package com.dynamis.sep_api.cobranca.domain.model;

import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de pagamento manual aplicado a uma {@link ParcelaCobranca} (Sprint 12 Task 12.4).
 *
 * <p>Cada recebimento exige {@code Idempotency-Key} unica. Reapresentacao da mesma key retorna o
 * mesmo recebimento sem criar novo. {@code identificadorExterno} mantem rastreabilidade pra
 * sistemas externos (ex.: protocolo de comprovante manual).
 */
@Entity
@Table(name = "recebimento")
public class Recebimento {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "parcela_id", nullable = false, updatable = false)
    private ParcelaCobranca parcela;

    @Column(name = "valor_recebido", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal valorRecebido;

    @Column(name = "data_recebimento", nullable = false, updatable = false)
    private OffsetDateTime dataRecebimento;

    @Column(name = "meio_pagamento", nullable = false, length = 40, updatable = false)
    private String meioPagamento;

    @Column(name = "identificador_externo", length = 100, updatable = false)
    private String identificadorExterno;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "observacao", length = 500, updatable = false)
    private String observacao;

    @Column(name = "registrado_por", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID registradoPor;

    protected Recebimento() {}

    private Recebimento(
            UUID id,
            ParcelaCobranca parcela,
            BigDecimal valorRecebido,
            OffsetDateTime dataRecebimento,
            String meioPagamento,
            String identificadorExterno,
            String idempotencyKey,
            String observacao,
            UUID registradoPor) {
        this.id = id;
        this.parcela = parcela;
        this.valorRecebido = valorRecebido;
        this.dataRecebimento = dataRecebimento;
        this.meioPagamento = meioPagamento;
        this.identificadorExterno = identificadorExterno;
        this.idempotencyKey = idempotencyKey;
        this.observacao = observacao;
        this.registradoPor = registradoPor;
    }

    static Recebimento criar(
            ParcelaCobranca parcela,
            BigDecimal valorRecebido,
            OffsetDateTime dataRecebimento,
            String meioPagamento,
            String identificadorExterno,
            String idempotencyKey,
            String observacao,
            UUID registradoPor) {
        Objects.requireNonNull(parcela, "parcela obrigatoria");
        Objects.requireNonNull(valorRecebido, "valorRecebido obrigatorio");
        if (valorRecebido.signum() <= 0) {
            throw new IllegalArgumentException("valorRecebido deve ser positivo");
        }
        Objects.requireNonNull(dataRecebimento, "dataRecebimento obrigatoria");
        Objects.requireNonNull(meioPagamento, "meioPagamento obrigatorio");
        if (meioPagamento.isBlank()) {
            throw new IllegalArgumentException("meioPagamento nao pode ser vazio");
        }
        Objects.requireNonNull(idempotencyKey, "idempotencyKey obrigatoria");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey nao pode ser vazia");
        }
        Objects.requireNonNull(registradoPor, "registradoPor obrigatorio");
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new Recebimento(
                id,
                parcela,
                valorRecebido.setScale(2, RoundingMode.HALF_UP),
                dataRecebimento,
                meioPagamento,
                identificadorExterno,
                idempotencyKey,
                observacao,
                registradoPor);
    }

    public UUID getId() {
        return id;
    }

    public ParcelaCobranca getParcela() {
        return parcela;
    }

    public BigDecimal getValorRecebido() {
        return valorRecebido;
    }

    public OffsetDateTime getDataRecebimento() {
        return dataRecebimento;
    }

    public String getMeioPagamento() {
        return meioPagamento;
    }

    public String getIdentificadorExterno() {
        return identificadorExterno;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getObservacao() {
        return observacao;
    }

    public UUID getRegistradoPor() {
        return registradoPor;
    }
}

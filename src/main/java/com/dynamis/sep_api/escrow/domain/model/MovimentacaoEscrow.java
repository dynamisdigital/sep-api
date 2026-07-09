package com.dynamis.sep_api.escrow.domain.model;

import com.dynamis.sep_api.escrow.domain.vo.StatusMovimentacao;
import com.dynamis.sep_api.escrow.domain.vo.TipoMovimentacao;
import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "movimentacao_escrow")
public class MovimentacaoEscrow extends EntidadeAuditavel {

    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(name = "tipo", nullable = false, length = 40)
    private String tipo; // serializa o sealed type pelo nome do permits

    @Column(name = "valor", nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StatusMovimentacao status;

    @Column(name = "data_movimentacao", nullable = false)
    private OffsetDateTime dataMovimentacao;

    @Column(name = "external_reference_id")
    private UUID externalReferenceId;

    protected MovimentacaoEscrow() {}

    private MovimentacaoEscrow(
            UUID id,
            Wallet wallet,
            String tipo,
            BigDecimal valor,
            String idempotencyKey,
            StatusMovimentacao status,
            OffsetDateTime dataMovimentacao,
            UUID externalReferenceId) {
        this.id = id;
        this.wallet = wallet;
        this.tipo = tipo;
        this.valor = valor;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.dataMovimentacao = dataMovimentacao;
        this.externalReferenceId = externalReferenceId;
    }

    /**
     * Cria movimentacao de {@link TipoMovimentacao.Recebimento} (Sprint 12 Task 12.4). Como a
     * cobranca registra manualmente apos confirmar a entrada do dinheiro, a movimentacao nasce em
     * {@link StatusMovimentacao#LIQUIDADA}.
     */
    public static MovimentacaoEscrow criarRecebimento(
            Wallet wallet,
            BigDecimal valor,
            String idempotencyKey,
            OffsetDateTime dataMovimentacao,
            UUID externalReferenceId) {
        validarCriacao(wallet, valor, idempotencyKey, dataMovimentacao);
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new MovimentacaoEscrow(
                id,
                wallet,
                tipoLabel(new TipoMovimentacao.Recebimento()),
                valor.setScale(2, RoundingMode.HALF_UP),
                idempotencyKey,
                StatusMovimentacao.LIQUIDADA,
                dataMovimentacao,
                externalReferenceId);
    }

    /**
     * Cria movimentacao de {@link TipoMovimentacao.Aporte} (Sprint 29 Task 29.2). O aporte
     * assistido nasce em {@link StatusMovimentacao#EM_PROCESSAMENTO}: o credito do saldo da wallet
     * ocorre somente na liquidacao (reconciliacao, Task 29.5).
     */
    public static MovimentacaoEscrow criarAporte(
            Wallet wallet,
            BigDecimal valor,
            String idempotencyKey,
            OffsetDateTime dataMovimentacao,
            UUID externalReferenceId) {
        validarCriacao(wallet, valor, idempotencyKey, dataMovimentacao);
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new MovimentacaoEscrow(
                id,
                wallet,
                tipoLabel(new TipoMovimentacao.Aporte()),
                valor.setScale(2, RoundingMode.HALF_UP),
                idempotencyKey,
                StatusMovimentacao.EM_PROCESSAMENTO,
                dataMovimentacao,
                externalReferenceId);
    }

    private static void validarCriacao(
            Wallet wallet, BigDecimal valor, String idempotencyKey, OffsetDateTime dataMovimentacao) {
        Objects.requireNonNull(wallet, "wallet obrigatoria");
        Objects.requireNonNull(valor, "valor obrigatorio");
        if (valor.signum() <= 0) {
            throw new IllegalArgumentException("valor deve ser positivo");
        }
        Objects.requireNonNull(idempotencyKey, "idempotencyKey obrigatoria");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey nao pode ser vazia");
        }
        Objects.requireNonNull(dataMovimentacao, "dataMovimentacao obrigatoria");
    }

    private static String tipoLabel(TipoMovimentacao tipo) {
        return tipo.getClass().getSimpleName();
    }

    /** Liquida a movimentacao apos reconciliacao (Sprint 29 Task 29.5). Estado terminal. */
    public void marcarLiquidada() {
        exigirEstado(StatusMovimentacao.EM_PROCESSAMENTO);
        this.status = StatusMovimentacao.LIQUIDADA;
    }

    /** Marca falha terminal da movimentacao apos reconciliacao (Sprint 29 Task 29.5). */
    public void marcarFalhou() {
        exigirEstado(StatusMovimentacao.EM_PROCESSAMENTO);
        this.status = StatusMovimentacao.FALHOU;
    }

    private void exigirEstado(StatusMovimentacao... permitidos) {
        for (StatusMovimentacao permitido : permitidos) {
            if (this.status == permitido) {
                return;
            }
        }
        // Mensagem carrega apenas o status atual — sem id, chave ou referencia externa.
        throw new IllegalStateException("transicao invalida a partir de " + this.status);
    }

    public UUID getId() {
        return id;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public String getTipo() {
        return tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public StatusMovimentacao getStatus() {
        return status;
    }

    public OffsetDateTime getDataMovimentacao() {
        return dataMovimentacao;
    }

    public UUID getExternalReferenceId() {
        return externalReferenceId;
    }
}

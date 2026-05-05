package com.dynamis.sep_api.escrow.domain.model;

import com.dynamis.sep_api.escrow.domain.vo.StatusMovimentacao;
import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
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
import java.time.OffsetDateTime;
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

    protected MovimentacaoEscrow() {}

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
}

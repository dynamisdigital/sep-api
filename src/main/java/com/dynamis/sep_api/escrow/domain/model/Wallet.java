package com.dynamis.sep_api.escrow.domain.model;

import com.dynamis.sep_api.escrow.domain.vo.TipoWallet;
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
import java.util.UUID;

@Entity
@Table(name = "wallet")
public class Wallet extends EntidadeAuditavel {

    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_escrow_id", nullable = false)
    private ContaEscrow contaEscrow;

    @Column(name = "proposta_id")
    private UUID propostaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_wallet", nullable = false, length = 40)
    private TipoWallet tipoWallet;

    @Column(name = "saldo", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldo;

    protected Wallet() {}

    public UUID getId() {
        return id;
    }

    public ContaEscrow getContaEscrow() {
        return contaEscrow;
    }

    public UUID getPropostaId() {
        return propostaId;
    }

    public TipoWallet getTipoWallet() {
        return tipoWallet;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }
}

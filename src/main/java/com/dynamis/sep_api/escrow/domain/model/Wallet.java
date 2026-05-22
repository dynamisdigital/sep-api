package com.dynamis.sep_api.escrow.domain.model;

import com.dynamis.sep_api.escrow.domain.vo.TipoWallet;
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
import java.util.Objects;
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

    private Wallet(UUID id, ContaEscrow contaEscrow, UUID propostaId, TipoWallet tipoWallet, BigDecimal saldoInicial) {
        this.id = id;
        this.contaEscrow = contaEscrow;
        this.propostaId = propostaId;
        this.tipoWallet = tipoWallet;
        this.saldo = saldoInicial.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Cria wallet associada a uma {@link ContaEscrow} e a uma proposta (Sprint 12 Task 12.4 cria
     * uma wallet por proposta sob a conta escrow tecnica). Saldo inicial obrigatoriamente zero.
     */
    public static Wallet criar(ContaEscrow contaEscrow, UUID propostaId, TipoWallet tipoWallet) {
        Objects.requireNonNull(contaEscrow, "contaEscrow obrigatoria");
        Objects.requireNonNull(tipoWallet, "tipoWallet obrigatorio");
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new Wallet(id, contaEscrow, propostaId, tipoWallet, BigDecimal.ZERO);
    }

    /**
     * Credita valor no saldo (Sprint 12 Task 12.4 — usada por {@code
     * RegistrarMovimentacaoEscrowUseCase} quando registra um recebimento). Encapsulada para
     * preservar invariante {@code saldo >= 0}.
     */
    public void creditar(BigDecimal valor) {
        Objects.requireNonNull(valor, "valor obrigatorio");
        if (valor.signum() <= 0) {
            throw new IllegalArgumentException("valor de credito deve ser positivo");
        }
        this.saldo = saldo.add(valor).setScale(2, RoundingMode.HALF_UP);
    }

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

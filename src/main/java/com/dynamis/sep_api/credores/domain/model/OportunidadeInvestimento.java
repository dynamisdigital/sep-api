package com.dynamis.sep_api.credores.domain.model;

import com.dynamis.sep_api.credores.domain.vo.StatusOportunidade;
import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Snapshot operacional de uma proposta de credito aprovada/formalizada, exposta como oportunidade
 * de investimento para empresas credoras (Sprint 17). 1:1 com a proposta de origem
 * ({@code propostaId} unico). Nao copia regra de credito — apenas materializa os dados minimos
 * necessarios para a credora avaliar a captacao.
 *
 * <p>O snapshot e criado/atualizado por um use case de sincronizacao explicito (Task 17.4) que le
 * propostas elegiveis via porta read-only; {@code credores} nao assina eventos de outros modulos.
 */
@Entity
@Table(name = "oportunidade_investimento")
public class OportunidadeInvestimento extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "proposta_id", columnDefinition = "uuid", nullable = false, unique = true, updatable = false)
    private UUID propostaId;

    @Column(name = "contrato_id", columnDefinition = "uuid")
    private UUID contratoId;

    @Column(name = "valor", nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(name = "prazo_meses", nullable = false)
    private int prazoMeses;

    @Column(name = "taxa_juros_mensal", precision = 9, scale = 6)
    private BigDecimal taxaJurosMensal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusOportunidade status;

    protected OportunidadeInvestimento() {
        // requerido pelo Hibernate
    }

    private OportunidadeInvestimento(
            UUID id, UUID propostaId, UUID contratoId, BigDecimal valor, int prazoMeses, BigDecimal taxaJurosMensal) {
        this.id = id;
        this.propostaId = propostaId;
        this.contratoId = contratoId;
        this.valor = valor;
        this.prazoMeses = prazoMeses;
        this.taxaJurosMensal = taxaJurosMensal;
        this.status = StatusOportunidade.DISPONIVEL;
    }

    public static OportunidadeInvestimento criar(
            UUID propostaId, UUID contratoId, BigDecimal valor, int prazoMeses, BigDecimal taxaJurosMensal) {
        java.util.Objects.requireNonNull(propostaId, "propostaId obrigatorio");
        java.util.Objects.requireNonNull(valor, "valor obrigatorio");
        if (valor.signum() < 0) {
            throw new IllegalArgumentException("valor nao pode ser negativo");
        }
        if (prazoMeses <= 0) {
            throw new IllegalArgumentException("prazoMeses deve ser positivo");
        }
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new OportunidadeInvestimento(id, propostaId, contratoId, valor, prazoMeses, taxaJurosMensal);
    }

    /** Atualiza o snapshot a partir da proposta/contrato de origem (sincronizacao idempotente). */
    public void atualizarSnapshot(UUID contratoId, BigDecimal valor, int prazoMeses, BigDecimal taxaJurosMensal) {
        this.contratoId = contratoId;
        this.valor = valor;
        this.prazoMeses = prazoMeses;
        this.taxaJurosMensal = taxaJurosMensal;
    }

    public void encerrar() {
        this.status = StatusOportunidade.ENCERRADA;
    }

    public boolean isDisponivel() {
        return this.status == StatusOportunidade.DISPONIVEL;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPropostaId() {
        return propostaId;
    }

    public UUID getContratoId() {
        return contratoId;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public int getPrazoMeses() {
        return prazoMeses;
    }

    public BigDecimal getTaxaJurosMensal() {
        return taxaJurosMensal;
    }

    public StatusOportunidade getStatus() {
        return status;
    }
}

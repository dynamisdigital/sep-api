package com.dynamis.sep_api.escrow.domain.model;

import com.dynamis.sep_api.escrow.domain.vo.StatusContaEscrow;
import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Conta escrow segregada conforme Resolucao CMN 4.656/2018 e ADR 0005.
 *
 * <p>Modelagem inicial da Sprint 1 — Sprint 12 Task 12.4 adiciona factory pra permitir criacao
 * controlada via use case interno {@code RegistrarMovimentacaoEscrowUseCase} (Pix/Celcoin real
 * fica pra Epic 15).
 */
@Entity
@Table(name = "conta_escrow")
public class ContaEscrow extends EntidadeAuditavel {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "titular", nullable = false, length = 255)
    private String titular;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StatusContaEscrow status;

    protected ContaEscrow() {
        // JPA
    }

    private ContaEscrow(UUID id, String externalId, String titular, StatusContaEscrow status) {
        this.id = id;
        this.externalId = externalId;
        this.titular = titular;
        this.status = status;
    }

    /**
     * Cria uma conta escrow interna em estado {@link StatusContaEscrow#ATIVA}. Sprint 12 usa
     * {@code titular} fixo "SEP-COBRANCA" pra agregada de recebimentos locais; Epic 15 trocara por
     * conta real do Celcoin via {@code EscrowProvider}.
     */
    public static ContaEscrow criar(String titular, StatusContaEscrow status) {
        Objects.requireNonNull(titular, "titular obrigatorio");
        if (titular.isBlank()) {
            throw new IllegalArgumentException("titular nao pode ser vazio");
        }
        Objects.requireNonNull(status, "status obrigatorio");
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new ContaEscrow(id, null, titular, status);
    }

    public UUID getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getTitular() {
        return titular;
    }

    public StatusContaEscrow getStatus() {
        return status;
    }
}

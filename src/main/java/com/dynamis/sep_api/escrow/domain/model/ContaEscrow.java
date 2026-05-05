package com.dynamis.sep_api.escrow.domain.model;

import com.dynamis.sep_api.escrow.domain.vo.StatusContaEscrow;
import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Conta escrow segregada conforme Resolucao CMN 4.656/2018 e ADR 0005.
 *
 * <p>Modelagem inicial da Sprint 1 - regra de negocio (abertura concreta via Celcoin, movimentacoes
 * reais) entra na Epic 15. Sprint 1 apenas garante que o schema e a estrutura JPA estao prontos.
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

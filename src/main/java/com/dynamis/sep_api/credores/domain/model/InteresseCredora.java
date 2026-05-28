package com.dynamis.sep_api.credores.domain.model;

import com.dynamis.sep_api.credores.domain.vo.StatusInteresseCredora;
import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Manifestacao de interesse de uma {@link EmpresaCredora} numa {@link OportunidadeInvestimento}
 * (Sprint 17). Unico interesse ATIVO por credora+oportunidade (UNIQUE parcial na V40). Sprint 17
 * nao faz matching financeiro — o interesse e operacional.
 */
@Entity
@Table(name = "interesse_credora")
public class InteresseCredora extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "empresa_credora_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID empresaCredoraId;

    @Column(name = "oportunidade_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID oportunidadeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusInteresseCredora status;

    protected InteresseCredora() {
        // requerido pelo Hibernate
    }

    private InteresseCredora(UUID id, UUID empresaCredoraId, UUID oportunidadeId) {
        this.id = id;
        this.empresaCredoraId = empresaCredoraId;
        this.oportunidadeId = oportunidadeId;
        this.status = StatusInteresseCredora.ATIVO;
    }

    public static InteresseCredora registrar(UUID empresaCredoraId, UUID oportunidadeId) {
        java.util.Objects.requireNonNull(empresaCredoraId, "empresaCredoraId obrigatorio");
        java.util.Objects.requireNonNull(oportunidadeId, "oportunidadeId obrigatorio");
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new InteresseCredora(id, empresaCredoraId, oportunidadeId);
    }

    /** Cancela o interesse. Idempotente: rechamar em interesse ja cancelado e no-op. */
    public void cancelar() {
        this.status = StatusInteresseCredora.CANCELADO;
    }

    public boolean isAtivo() {
        return this.status == StatusInteresseCredora.ATIVO;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmpresaCredoraId() {
        return empresaCredoraId;
    }

    public UUID getOportunidadeId() {
        return oportunidadeId;
    }

    public StatusInteresseCredora getStatus() {
        return status;
    }
}

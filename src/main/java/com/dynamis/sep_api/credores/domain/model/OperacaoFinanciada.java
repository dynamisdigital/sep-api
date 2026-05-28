package com.dynamis.sep_api.credores.domain.model;

import com.dynamis.sep_api.credores.domain.vo.StatusOperacaoFinanciada;
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
 * Associacao operacional entre uma {@link EmpresaCredora} e um contrato financiado, compondo a
 * carteira da credora (Sprint 17). Referencia o contrato e a oportunidade de origem por UUID
 * logico. Sprint 17 nao move recurso financeiro real — representa apenas o vinculo operacional.
 */
@Entity
@Table(name = "operacao_financiada")
public class OperacaoFinanciada extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "empresa_credora_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID empresaCredoraId;

    @Column(name = "contrato_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID contratoId;

    @Column(name = "oportunidade_id", columnDefinition = "uuid", updatable = false)
    private UUID oportunidadeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusOperacaoFinanciada status;

    @Column(name = "justificativa", nullable = false, length = 500, updatable = false)
    private String justificativa;

    protected OperacaoFinanciada() {
        // requerido pelo Hibernate
    }

    private OperacaoFinanciada(
            UUID id, UUID empresaCredoraId, UUID contratoId, UUID oportunidadeId, String justificativa) {
        this.id = id;
        this.empresaCredoraId = empresaCredoraId;
        this.contratoId = contratoId;
        this.oportunidadeId = oportunidadeId;
        this.justificativa = justificativa;
        this.status = StatusOperacaoFinanciada.ASSOCIADA;
    }

    /**
     * Cria a associacao operacional assistida entre credora e contrato (Sprint 17). {@code
     * justificativa} registra a motivacao operacional da associacao (sem matching/aporte real).
     */
    public static OperacaoFinanciada associar(
            UUID empresaCredoraId, UUID contratoId, UUID oportunidadeId, String justificativa) {
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new OperacaoFinanciada(id, empresaCredoraId, contratoId, oportunidadeId, justificativa);
    }

    public void encerrar() {
        this.status = StatusOperacaoFinanciada.ENCERRADA;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmpresaCredoraId() {
        return empresaCredoraId;
    }

    public UUID getContratoId() {
        return contratoId;
    }

    public UUID getOportunidadeId() {
        return oportunidadeId;
    }

    public StatusOperacaoFinanciada getStatus() {
        return status;
    }

    public String getJustificativa() {
        return justificativa;
    }
}

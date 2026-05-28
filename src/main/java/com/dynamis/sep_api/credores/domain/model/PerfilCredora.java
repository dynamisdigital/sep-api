package com.dynamis.sep_api.credores.domain.model;

import com.dynamis.sep_api.credores.domain.vo.TipoCredora;
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
 * Perfil operacional 1:1 com {@link EmpresaCredora}. Concentra os parametros operacionais
 * declarados no cadastro, mantidos separados do agregado raiz porque evoluem de forma
 * independente nas sprints de carteira/aportes (Sprint 17+).
 *
 * <p>{@code capacidadeAporte} e o teto declarado de aporte; opcional no cadastro inicial.
 */
@Entity
@Table(name = "perfil_credora")
public class PerfilCredora extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "empresa_credora_id", columnDefinition = "uuid", nullable = false, unique = true, updatable = false)
    private UUID empresaCredoraId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_credora", nullable = false, length = 30)
    private TipoCredora tipoCredora;

    @Column(name = "capacidade_aporte", precision = 19, scale = 2)
    private BigDecimal capacidadeAporte;

    protected PerfilCredora() {
        // requerido pelo Hibernate
    }

    private PerfilCredora(UUID id, UUID empresaCredoraId, TipoCredora tipoCredora, BigDecimal capacidadeAporte) {
        this.id = id;
        this.empresaCredoraId = empresaCredoraId;
        this.tipoCredora = tipoCredora;
        this.capacidadeAporte = capacidadeAporte;
    }

    public static PerfilCredora criar(UUID empresaCredoraId, TipoCredora tipoCredora, BigDecimal capacidadeAporte) {
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new PerfilCredora(id, empresaCredoraId, tipoCredora, capacidadeAporte);
    }

    public void atualizar(TipoCredora tipoCredora, BigDecimal capacidadeAporte) {
        if (tipoCredora != null) {
            this.tipoCredora = tipoCredora;
        }
        this.capacidadeAporte = capacidadeAporte;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmpresaCredoraId() {
        return empresaCredoraId;
    }

    public TipoCredora getTipoCredora() {
        return tipoCredora;
    }

    public BigDecimal getCapacidadeAporte() {
        return capacidadeAporte;
    }
}

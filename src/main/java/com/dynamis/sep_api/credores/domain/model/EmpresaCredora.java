package com.dynamis.sep_api.credores.domain.model;

import com.dynamis.sep_api.credores.domain.vo.StatusCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusElegibilidade;
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
 * Agregado raiz da jornada credora. Representa a empresa que aporta recursos na plataforma,
 * vinculada a um {@code usuario} autenticado e a um onboarding PJ ({@code onboardingId} aponta
 * para uma {@code SolicitacaoOnboarding} do tipo {@code EMPRESA} ja aprovada no modulo
 * {@code onboarding}).
 *
 * <p>O cadastro nao reexecuta KYB/PLD: a elegibilidade ({@link StatusElegibilidade}) deriva do
 * resultado de onboarding consultado via porta read-only (Sprint 16, Task 4). {@code CADASTRADA}
 * com {@code PENDENTE} e o estado inicial; uma decisao {@code ELEGIVEL} promove a credora a
 * {@code ATIVA}.
 */
@Entity
@Table(name = "empresa_credora")
public class EmpresaCredora extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "usuario_id", columnDefinition = "uuid", nullable = false, unique = true, updatable = false)
    private UUID usuarioId;

    @Column(name = "onboarding_id", columnDefinition = "uuid", nullable = false, unique = true, updatable = false)
    private UUID onboardingId;

    @Column(name = "cnpj", nullable = false, length = 14, unique = true, updatable = false)
    private String cnpj;

    @Column(name = "razao_social", nullable = false, length = 255)
    private String razaoSocial;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusCredora status;

    @Enumerated(EnumType.STRING)
    @Column(name = "elegibilidade", nullable = false, length = 20)
    private StatusElegibilidade elegibilidade;

    @Column(name = "motivo_inelegibilidade", length = 500)
    private String motivoInelegibilidade;

    protected EmpresaCredora() {
        // requerido pelo Hibernate
    }

    private EmpresaCredora(UUID id, UUID usuarioId, UUID onboardingId, String cnpj, String razaoSocial) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.onboardingId = onboardingId;
        this.cnpj = cnpj;
        this.razaoSocial = razaoSocial;
        this.status = StatusCredora.CADASTRADA;
        this.elegibilidade = StatusElegibilidade.PENDENTE;
    }

    /**
     * Cria uma credora recem-cadastrada. {@code cnpj} deve chegar normalizado (14 digitos); a
     * validacao de formato acontece na borda (DTO) e o valor canonico vem do onboarding PJ.
     */
    public static EmpresaCredora cadastrar(UUID usuarioId, UUID onboardingId, String cnpj, String razaoSocial) {
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new EmpresaCredora(id, usuarioId, onboardingId, cnpj, razaoSocial);
    }

    /** Registra a decisao de elegibilidade derivada do onboarding e ajusta o status cadastral. */
    public void registrarElegivel() {
        this.elegibilidade = StatusElegibilidade.ELEGIVEL;
        this.motivoInelegibilidade = null;
        if (this.status == StatusCredora.CADASTRADA) {
            this.status = StatusCredora.ATIVA;
        }
    }

    /** Registra inelegibilidade com o motivo da decisao; mantem a credora fora do estado ATIVA. */
    public void registrarInelegivel(String motivo) {
        this.elegibilidade = StatusElegibilidade.INELEGIVEL;
        this.motivoInelegibilidade = motivo;
    }

    public void atualizarRazaoSocial(String razaoSocial) {
        if (razaoSocial != null && !razaoSocial.isBlank()) {
            this.razaoSocial = razaoSocial;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID getOnboardingId() {
        return onboardingId;
    }

    public String getCnpj() {
        return cnpj;
    }

    public String getRazaoSocial() {
        return razaoSocial;
    }

    public StatusCredora getStatus() {
        return status;
    }

    public StatusElegibilidade getElegibilidade() {
        return elegibilidade;
    }

    public String getMotivoInelegibilidade() {
        return motivoInelegibilidade;
    }
}

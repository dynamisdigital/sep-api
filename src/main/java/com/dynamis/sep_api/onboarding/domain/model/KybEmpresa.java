package com.dynamis.sep_api.onboarding.domain.model;

import com.dynamis.sep_api.onboarding.domain.vo.Cnpj;
import com.dynamis.sep_api.onboarding.domain.vo.PorteEmpresa;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSocietario;
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
 * Agregado KYB de pessoa juridica — 1:1 com {@link SolicitacaoOnboarding} do tipo
 * {@code EMPRESA}. Concentra os dados cadastrais informados no inicio do onboarding PJ
 * (Sprint 7); resultado da consulta ao provider vive em {@link ConsultaCNPJ} (1:1) e
 * representantes legais em {@link RepresentanteLegal} (1:N).
 */
@Entity
@Table(name = "kyb_empresa")
public class KybEmpresa extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "solicitacao_id", columnDefinition = "uuid", nullable = false, unique = true, updatable = false)
    private UUID solicitacaoId;

    @Column(name = "cnpj", nullable = false, length = 14, updatable = false)
    private String cnpj;

    @Column(name = "razao_social", nullable = false, length = 255)
    private String razaoSocial;

    @Column(name = "nome_fantasia", length = 255)
    private String nomeFantasia;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_societario", length = 20)
    private TipoSocietario tipoSocietario;

    @Enumerated(EnumType.STRING)
    @Column(name = "porte", length = 20)
    private PorteEmpresa porte;

    protected KybEmpresa() {
        // requerido pelo Hibernate
    }

    private KybEmpresa(
            UUID id,
            UUID solicitacaoId,
            Cnpj cnpj,
            String razaoSocial,
            String nomeFantasia,
            TipoSocietario tipoSocietario,
            PorteEmpresa porte) {
        this.id = id;
        this.solicitacaoId = solicitacaoId;
        this.cnpj = cnpj.valor();
        this.razaoSocial = razaoSocial;
        this.nomeFantasia = nomeFantasia;
        this.tipoSocietario = tipoSocietario;
        this.porte = porte;
    }

    public static KybEmpresa criar(
            UUID solicitacaoId,
            Cnpj cnpj,
            String razaoSocial,
            String nomeFantasia,
            TipoSocietario tipoSocietario,
            PorteEmpresa porte) {
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new KybEmpresa(id, solicitacaoId, cnpj, razaoSocial, nomeFantasia, tipoSocietario, porte);
    }

    /** Atualiza dados cadastrais (apos retorno do KybProvider). */
    public void atualizarDadosCadastrais(String razaoSocial, String nomeFantasia) {
        if (razaoSocial != null && !razaoSocial.isBlank()) {
            this.razaoSocial = razaoSocial;
        }
        this.nomeFantasia = nomeFantasia;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSolicitacaoId() {
        return solicitacaoId;
    }

    public String getCnpj() {
        return cnpj;
    }

    public String getRazaoSocial() {
        return razaoSocial;
    }

    public String getNomeFantasia() {
        return nomeFantasia;
    }

    public TipoSocietario getTipoSocietario() {
        return tipoSocietario;
    }

    public PorteEmpresa getPorte() {
        return porte;
    }
}

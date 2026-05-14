package com.dynamis.sep_api.onboarding.domain.model;

import com.dynamis.sep_api.onboarding.domain.vo.SituacaoCadastral;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Snapshot do resultado de consulta de CNPJ retornado pelo {@code KybProvider}. 1:1 com
 * {@link KybEmpresa}. Guarda payload bruto JSONB para trilha auditavel regulatoria (CMN
 * 4.656/2018). Payload bruto NUNCA entra em audit_log_seguranca ou logs publicos.
 */
@Entity
@Table(name = "consulta_cnpj")
public class ConsultaCNPJ {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "kyb_empresa_id", columnDefinition = "uuid", nullable = false, unique = true, updatable = false)
    private UUID kybEmpresaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao_cadastral", nullable = false, length = 20)
    private SituacaoCadastral situacaoCadastral;

    @Column(name = "razao_social", length = 255)
    private String razaoSocial;

    @Column(name = "nome_fantasia", length = 255)
    private String nomeFantasia;

    @Column(name = "cnae_principal", length = 20)
    private String cnaePrincipal;

    @Column(name = "cnaes_secundarios", columnDefinition = "text")
    private String cnaesSecundarios;

    @Column(name = "capital_social", precision = 19, scale = 2)
    private BigDecimal capitalSocial;

    @Column(name = "data_abertura")
    private LocalDate dataAbertura;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_provider", columnDefinition = "jsonb")
    private String payloadProvider;

    @Column(name = "data_consulta", nullable = false, updatable = false)
    private OffsetDateTime dataConsulta;

    protected ConsultaCNPJ() {
        // requerido pelo Hibernate
    }

    private ConsultaCNPJ(
            UUID id,
            UUID kybEmpresaId,
            SituacaoCadastral situacaoCadastral,
            String razaoSocial,
            String nomeFantasia,
            String cnaePrincipal,
            String cnaesSecundarios,
            BigDecimal capitalSocial,
            LocalDate dataAbertura,
            String payloadProvider) {
        this.id = id;
        this.kybEmpresaId = kybEmpresaId;
        this.situacaoCadastral = situacaoCadastral;
        this.razaoSocial = razaoSocial;
        this.nomeFantasia = nomeFantasia;
        this.cnaePrincipal = cnaePrincipal;
        this.cnaesSecundarios = cnaesSecundarios;
        this.capitalSocial = capitalSocial;
        this.dataAbertura = dataAbertura;
        this.payloadProvider = payloadProvider;
        this.dataConsulta = OffsetDateTime.now();
    }

    public static ConsultaCNPJ registrar(
            UUID kybEmpresaId,
            SituacaoCadastral situacaoCadastral,
            String razaoSocial,
            String nomeFantasia,
            String cnaePrincipal,
            String cnaesSecundarios,
            BigDecimal capitalSocial,
            LocalDate dataAbertura,
            String payloadProvider) {
        if (situacaoCadastral == null) {
            throw new IllegalArgumentException("situacaoCadastral e obrigatorio em ConsultaCNPJ");
        }
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new ConsultaCNPJ(
                id,
                kybEmpresaId,
                situacaoCadastral,
                razaoSocial,
                nomeFantasia,
                cnaePrincipal,
                cnaesSecundarios,
                capitalSocial,
                dataAbertura,
                payloadProvider);
    }

    public UUID getId() {
        return id;
    }

    public UUID getKybEmpresaId() {
        return kybEmpresaId;
    }

    public SituacaoCadastral getSituacaoCadastral() {
        return situacaoCadastral;
    }

    public String getRazaoSocial() {
        return razaoSocial;
    }

    public String getNomeFantasia() {
        return nomeFantasia;
    }

    public String getCnaePrincipal() {
        return cnaePrincipal;
    }

    public String getCnaesSecundarios() {
        return cnaesSecundarios;
    }

    public BigDecimal getCapitalSocial() {
        return capitalSocial;
    }

    public LocalDate getDataAbertura() {
        return dataAbertura;
    }

    public String getPayloadProvider() {
        return payloadProvider;
    }

    public OffsetDateTime getDataConsulta() {
        return dataConsulta;
    }
}

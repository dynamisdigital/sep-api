package com.dynamis.sep_api.onboarding.domain.model;

import com.dynamis.sep_api.onboarding.domain.vo.AlvoPld;
import com.dynamis.sep_api.onboarding.domain.vo.BasePld;
import com.dynamis.sep_api.onboarding.domain.vo.SeveridadePld;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro de uma consulta PLD individual — um alvo (PF, PJ ou representante) em uma das
 * 4 bases obrigatorias (COAF/OFAC/INTERPOL/MTE). N:1 com {@link SolicitacaoOnboarding}.
 *
 * <p>Retencao minima de 5 anos por LGPD Art. 16 — calcula {@link #retencaoAte} = data
 * da consulta + 5 anos.
 *
 * <p>Payload bruto fica em {@link #payloadProvider}; detalhes de hit (motivo, severidade,
 * data_inclusao) nunca sao expostos publicamente — uso restrito a auditoria interna.
 */
@Entity
@Table(name = "consulta_pld")
public class ConsultaPld {

    private static final int ANOS_RETENCAO_LGPD = 5;

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "solicitacao_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID solicitacaoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alvo_tipo", nullable = false, length = 20, updatable = false)
    private AlvoPld alvoTipo;

    @Column(name = "alvo_documento", nullable = false, length = 14, updatable = false)
    private String alvoDocumento;

    @Enumerated(EnumType.STRING)
    @Column(name = "base", nullable = false, length = 20, updatable = false)
    private BasePld base;

    @Column(name = "hit", nullable = false)
    private boolean hit;

    @Column(name = "motivo", columnDefinition = "text")
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "severidade", length = 20)
    private SeveridadePld severidade;

    @Column(name = "data_inclusao")
    private LocalDate dataInclusao;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_provider", columnDefinition = "jsonb")
    private String payloadProvider;

    @Column(name = "data_consulta", nullable = false, updatable = false)
    private OffsetDateTime dataConsulta;

    @Column(name = "retencao_ate", nullable = false, updatable = false)
    private LocalDate retencaoAte;

    protected ConsultaPld() {
        // requerido pelo Hibernate
    }

    private ConsultaPld(
            UUID id,
            UUID solicitacaoId,
            AlvoPld alvoTipo,
            String alvoDocumento,
            BasePld base,
            boolean hit,
            String motivo,
            SeveridadePld severidade,
            LocalDate dataInclusao,
            String payloadProvider) {
        this.id = id;
        this.solicitacaoId = solicitacaoId;
        this.alvoTipo = alvoTipo;
        this.alvoDocumento = alvoDocumento;
        this.base = base;
        this.hit = hit;
        this.motivo = motivo;
        this.severidade = severidade;
        this.dataInclusao = dataInclusao;
        this.payloadProvider = payloadProvider;
        this.dataConsulta = OffsetDateTime.now();
        this.retencaoAte = this.dataConsulta.toLocalDate().plusYears(ANOS_RETENCAO_LGPD);
    }

    /** Consulta limpa em uma base (sem hit). */
    public static ConsultaPld limpa(
            UUID solicitacaoId, AlvoPld alvoTipo, String alvoDocumento, BasePld base, String payloadProvider) {
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new ConsultaPld(
                id, solicitacaoId, alvoTipo, alvoDocumento, base, false, null, null, null, payloadProvider);
    }

    /** Consulta com hit em uma base. */
    public static ConsultaPld hit(
            UUID solicitacaoId,
            AlvoPld alvoTipo,
            String alvoDocumento,
            BasePld base,
            String motivo,
            SeveridadePld severidade,
            LocalDate dataInclusao,
            String payloadProvider) {
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new ConsultaPld(
                id,
                solicitacaoId,
                alvoTipo,
                alvoDocumento,
                base,
                true,
                motivo,
                severidade,
                dataInclusao,
                payloadProvider);
    }

    public UUID getId() {
        return id;
    }

    public UUID getSolicitacaoId() {
        return solicitacaoId;
    }

    public AlvoPld getAlvoTipo() {
        return alvoTipo;
    }

    public String getAlvoDocumento() {
        return alvoDocumento;
    }

    public BasePld getBase() {
        return base;
    }

    public boolean isHit() {
        return hit;
    }

    public String getMotivo() {
        return motivo;
    }

    public SeveridadePld getSeveridade() {
        return severidade;
    }

    public LocalDate getDataInclusao() {
        return dataInclusao;
    }

    public String getPayloadProvider() {
        return payloadProvider;
    }

    public OffsetDateTime getDataConsulta() {
        return dataConsulta;
    }

    public LocalDate getRetencaoAte() {
        return retencaoAte;
    }
}

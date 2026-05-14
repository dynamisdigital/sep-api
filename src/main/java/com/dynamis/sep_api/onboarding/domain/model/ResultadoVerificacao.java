package com.dynamis.sep_api.onboarding.domain.model;

import com.dynamis.sep_api.onboarding.domain.exception.StatusOnboardingInvalidoException;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Resultado final da verificacao KYC. 1:1 com {@link SolicitacaoOnboarding}. Guarda o payload
 * cru do provider (Celcoin) em JSONB para trilha auditavel regulatoria (CMN 4.656/2018).
 *
 * <p>Payload bruto NUNCA entra no {@code audit_log_seguranca} — fica apenas aqui.
 */
@Entity
@Table(name = "resultado_verificacao")
public class ResultadoVerificacao {

    /**
     * Status aceitos pelo ResultadoVerificacao: somente os finais de KYC/KYB
     * (pre-PLD). Pos-PLD ({@code APROVADO_FINAL}, {@code REPROVADO_PLD}) vive em
     * {@code ConsultaPld} + transicoes da {@code SolicitacaoOnboarding}, nao aqui.
     */
    private static final Set<StatusOnboarding> STATUS_RESULTADO_KYC_KYB =
            EnumSet.of(StatusOnboarding.APROVADO, StatusOnboarding.REPROVADO, StatusOnboarding.PENDENCIA);

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "solicitacao_id", columnDefinition = "uuid", nullable = false, unique = true, updatable = false)
    private UUID solicitacaoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_final", nullable = false, length = 40)
    private StatusOnboarding statusFinal;

    @Column(name = "motivo")
    private String motivo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_provider", columnDefinition = "jsonb")
    private String payloadProvider;

    @Column(name = "data_resultado", nullable = false, updatable = false)
    private OffsetDateTime dataResultado;

    protected ResultadoVerificacao() {
        // requerido pelo Hibernate
    }

    private ResultadoVerificacao(
            UUID id, UUID solicitacaoId, StatusOnboarding statusFinal, String motivo, String payloadProvider) {
        this.id = id;
        this.solicitacaoId = solicitacaoId;
        this.statusFinal = statusFinal;
        this.motivo = motivo;
        this.payloadProvider = payloadProvider;
        this.dataResultado = OffsetDateTime.now();
    }

    public static ResultadoVerificacao registrar(
            UUID solicitacaoId, StatusOnboarding statusFinal, String motivo, String payloadProvider) {
        if (statusFinal == null) {
            throw new StatusOnboardingInvalidoException("statusFinal e obrigatorio em ResultadoVerificacao");
        }
        if (!STATUS_RESULTADO_KYC_KYB.contains(statusFinal)) {
            throw new StatusOnboardingInvalidoException("registrarResultado", statusFinal);
        }
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new ResultadoVerificacao(id, solicitacaoId, statusFinal, motivo, payloadProvider);
    }

    public UUID getId() {
        return id;
    }

    public UUID getSolicitacaoId() {
        return solicitacaoId;
    }

    public StatusOnboarding getStatusFinal() {
        return statusFinal;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getPayloadProvider() {
        return payloadProvider;
    }

    public OffsetDateTime getDataResultado() {
        return dataResultado;
    }
}

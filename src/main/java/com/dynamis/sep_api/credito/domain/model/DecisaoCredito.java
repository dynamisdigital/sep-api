package com.dynamis.sep_api.credito.domain.model;

import com.dynamis.sep_api.credito.domain.vo.OrigemDecisao;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Decisao final consolidada da proposta (Sprint 8). Relacao 1:1 com {@code PropostaCredito} —
 * gravada apenas quando proposta atinge estado final ({@code APROVADA} ou {@code REJEITADA}).
 *
 * <p>{@link OrigemDecisao#MOTOR} indica rejeicao automatica (bloqueio absoluto, ex.: onboarding
 * nao aprovado). {@link OrigemDecisao#MANUAL} indica decisao por parecer financeiro; nesse caso
 * {@code parecerId} aponta para o {@link ParecerCredito} que materializou a decisao.
 */
@Entity
@Table(name = "decisao_credito")
public class DecisaoCredito {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "proposta_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID propostaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_final", nullable = false, length = 40)
    private StatusProposta statusFinal;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20)
    private OrigemDecisao origem;

    @Column(name = "score_motor")
    private Integer scoreMotor;

    @Column(name = "parecer_id", columnDefinition = "uuid")
    private UUID parecerId;

    @Column(name = "data_decisao", nullable = false)
    private OffsetDateTime dataDecisao;

    protected DecisaoCredito() {
        // Hibernate
    }

    private DecisaoCredito(
            UUID id,
            UUID propostaId,
            StatusProposta statusFinal,
            OrigemDecisao origem,
            Integer scoreMotor,
            UUID parecerId) {
        this.id = id;
        this.propostaId = propostaId;
        this.statusFinal = statusFinal;
        this.origem = origem;
        this.scoreMotor = scoreMotor;
        this.parecerId = parecerId;
        this.dataDecisao = OffsetDateTime.now();
    }

    public static DecisaoCredito porMotor(UUID propostaId, StatusProposta statusFinal, Integer scoreMotor) {
        if (statusFinal != StatusProposta.REJEITADA) {
            throw new IllegalArgumentException(
                    "decisao por motor so suporta REJEITADA na Sprint 8; recebido " + statusFinal);
        }
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new DecisaoCredito(id, propostaId, statusFinal, OrigemDecisao.MOTOR, scoreMotor, null);
    }

    public static DecisaoCredito porParecer(
            UUID propostaId, StatusProposta statusFinal, Integer scoreMotor, UUID parecerId) {
        if (statusFinal != StatusProposta.APROVADA && statusFinal != StatusProposta.REJEITADA) {
            throw new IllegalArgumentException("decisao manual exige status final; recebido " + statusFinal);
        }
        if (parecerId == null) {
            throw new IllegalArgumentException("parecerId obrigatorio em decisao manual");
        }
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new DecisaoCredito(id, propostaId, statusFinal, OrigemDecisao.MANUAL, scoreMotor, parecerId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getPropostaId() {
        return propostaId;
    }

    public StatusProposta getStatusFinal() {
        return statusFinal;
    }

    public OrigemDecisao getOrigem() {
        return origem;
    }

    public Integer getScoreMotor() {
        return scoreMotor;
    }

    public UUID getParecerId() {
        return parecerId;
    }

    public OffsetDateTime getDataDecisao() {
        return dataDecisao;
    }
}

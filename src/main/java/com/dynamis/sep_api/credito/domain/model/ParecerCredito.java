package com.dynamis.sep_api.credito.domain.model;

import com.dynamis.sep_api.credito.domain.exception.PropostaInvalidaException;
import com.dynamis.sep_api.credito.domain.vo.DecisaoParecer;
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
 * Parecer manual de operador financeiro (Sprint 8 Task 8.4). N:1 com {@code PropostaCredito}.
 * {@code versao} aumenta a cada novo parecer pra preservar historico (reaberturas em PENDENCIA).
 * {@code scoreMotorSnapshot} guarda o score do motor no momento do parecer pra auditoria comparar
 * sugestao automatica vs decisao manual (CMN 4.656/2018 Art. 9 — trilha de "por que aprovou").
 */
@Entity
@Table(name = "parecer_credito")
public class ParecerCredito {

    private static final int JUSTIFICATIVA_MIN = 10;
    private static final int JUSTIFICATIVA_MAX = 1000;

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "proposta_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID propostaId;

    @Column(name = "parecerista_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID pareceristaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decisao", nullable = false, length = 20)
    private DecisaoParecer decisao;

    @Column(name = "justificativa", nullable = false, length = JUSTIFICATIVA_MAX)
    private String justificativa;

    @Column(name = "score_motor_snapshot")
    private Integer scoreMotorSnapshot;

    @Column(name = "versao", nullable = false)
    private int versao;

    @Column(name = "data_parecer", nullable = false)
    private OffsetDateTime dataParecer;

    protected ParecerCredito() {
        // Hibernate
    }

    private ParecerCredito(
            UUID id,
            UUID propostaId,
            UUID pareceristaId,
            DecisaoParecer decisao,
            String justificativa,
            Integer scoreMotorSnapshot,
            int versao) {
        this.id = id;
        this.propostaId = propostaId;
        this.pareceristaId = pareceristaId;
        this.decisao = decisao;
        this.justificativa = justificativa;
        this.scoreMotorSnapshot = scoreMotorSnapshot;
        this.versao = versao;
        this.dataParecer = OffsetDateTime.now();
    }

    public static ParecerCredito registrar(
            UUID propostaId,
            UUID pareceristaId,
            DecisaoParecer decisao,
            String justificativa,
            Integer scoreMotorSnapshot,
            int versao) {
        if (justificativa == null || justificativa.isBlank()) {
            throw new PropostaInvalidaException("justificativa obrigatoria");
        }
        String trimmed = justificativa.trim();
        if (trimmed.length() < JUSTIFICATIVA_MIN) {
            throw new PropostaInvalidaException(
                    "justificativa deve ter pelo menos " + JUSTIFICATIVA_MIN + " caracteres");
        }
        if (trimmed.length() > JUSTIFICATIVA_MAX) {
            throw new PropostaInvalidaException("justificativa nao pode exceder " + JUSTIFICATIVA_MAX + " caracteres");
        }
        if (versao < 1) {
            throw new PropostaInvalidaException("versao do parecer deve ser >= 1");
        }
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new ParecerCredito(id, propostaId, pareceristaId, decisao, trimmed, scoreMotorSnapshot, versao);
    }

    public UUID getId() {
        return id;
    }

    public UUID getPropostaId() {
        return propostaId;
    }

    public UUID getPareceristaId() {
        return pareceristaId;
    }

    public DecisaoParecer getDecisao() {
        return decisao;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public Integer getScoreMotorSnapshot() {
        return scoreMotorSnapshot;
    }

    public int getVersao() {
        return versao;
    }

    public OffsetDateTime getDataParecer() {
        return dataParecer;
    }
}

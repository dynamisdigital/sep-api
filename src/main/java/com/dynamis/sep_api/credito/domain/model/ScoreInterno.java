package com.dynamis.sep_api.credito.domain.model;

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
 * Score interno produzido pelo {@code MotorRegrasCredito} (Sprint 8 Task 8.2). Relacao 1:1 com
 * {@code PropostaCredito} — sobrescrito a cada nova execucao do motor (raro nesta sprint, mas
 * previsto pra reavaliacoes pos-PENDENCIA na Task 8.4).
 */
@Entity
@Table(name = "score_interno")
public class ScoreInterno {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "proposta_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID propostaId;

    @Column(name = "valor", nullable = false)
    private int valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_sugerido", nullable = false, length = 40)
    private StatusProposta statusSugerido;

    @Column(name = "falhas", nullable = false)
    private int falhas;

    @Column(name = "pendencias", nullable = false)
    private int pendencias;

    @Column(name = "data_calculo", nullable = false)
    private OffsetDateTime dataCalculo;

    protected ScoreInterno() {
        // Hibernate
    }

    private ScoreInterno(
            UUID id, UUID propostaId, int valor, StatusProposta statusSugerido, int falhas, int pendencias) {
        this.id = id;
        this.propostaId = propostaId;
        this.valor = valor;
        this.statusSugerido = statusSugerido;
        this.falhas = falhas;
        this.pendencias = pendencias;
        this.dataCalculo = OffsetDateTime.now();
    }

    public static ScoreInterno calculado(
            UUID propostaId, int valor, StatusProposta statusSugerido, int falhas, int pendencias) {
        if (valor < 0 || valor > 1000) {
            throw new IllegalArgumentException("score deve estar entre 0 e 1000; recebido " + valor);
        }
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new ScoreInterno(id, propostaId, valor, statusSugerido, falhas, pendencias);
    }

    public UUID getId() {
        return id;
    }

    public UUID getPropostaId() {
        return propostaId;
    }

    public int getValor() {
        return valor;
    }

    public StatusProposta getStatusSugerido() {
        return statusSugerido;
    }

    public int getFalhas() {
        return falhas;
    }

    public int getPendencias() {
        return pendencias;
    }

    public OffsetDateTime getDataCalculo() {
        return dataCalculo;
    }
}

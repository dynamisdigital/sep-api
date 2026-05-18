package com.dynamis.sep_api.credito.domain.model;

import com.dynamis.sep_api.credito.domain.vo.ResultadoRegra;
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
 * Snapshot de uma regra avaliada pelo motor (Sprint 8 Task 8.2/8.3). N:1 com {@code
 * PropostaCredito}. Persistir todas as regras permite auditar "por que aprovou/rejeitou" em
 * eventuais contestacoes regulatorias (Resolucao CMN 4.656/2018 Art. 9).
 */
@Entity
@Table(name = "regra_credito_avaliada")
public class RegraCreditoAvaliada {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "proposta_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID propostaId;

    @Column(name = "nome_regra", nullable = false, length = 80)
    private String nomeRegra;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false, length = 20)
    private ResultadoRegra resultado;

    @Column(name = "motivo", length = 500)
    private String motivo;

    @Column(name = "bloqueante", nullable = false)
    private boolean bloqueante;

    @Column(name = "data_avaliacao", nullable = false)
    private OffsetDateTime dataAvaliacao;

    protected RegraCreditoAvaliada() {
        // Hibernate
    }

    private RegraCreditoAvaliada(
            UUID id, UUID propostaId, String nomeRegra, ResultadoRegra resultado, String motivo, boolean bloqueante) {
        this.id = id;
        this.propostaId = propostaId;
        this.nomeRegra = nomeRegra;
        this.resultado = resultado;
        this.motivo = motivo;
        this.bloqueante = bloqueante;
        this.dataAvaliacao = OffsetDateTime.now();
    }

    public static RegraCreditoAvaliada registrar(
            UUID propostaId, String nomeRegra, ResultadoRegra resultado, String motivo, boolean bloqueante) {
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new RegraCreditoAvaliada(id, propostaId, nomeRegra, resultado, motivo, bloqueante);
    }

    public UUID getId() {
        return id;
    }

    public UUID getPropostaId() {
        return propostaId;
    }

    public String getNomeRegra() {
        return nomeRegra;
    }

    public ResultadoRegra getResultado() {
        return resultado;
    }

    public String getMotivo() {
        return motivo;
    }

    public boolean isBloqueante() {
        return bloqueante;
    }

    public OffsetDateTime getDataAvaliacao() {
        return dataAvaliacao;
    }
}

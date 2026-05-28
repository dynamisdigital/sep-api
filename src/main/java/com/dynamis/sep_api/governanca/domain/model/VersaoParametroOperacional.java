package com.dynamis.sep_api.governanca.domain.model;

import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Historico imutavel de uma alteracao de {@link ParametroOperacional} (Sprint 18). Registra valor
 * anterior, valor novo, ator e justificativa. {@code versao} corresponde a nova versao do
 * parametro apos a alteracao.
 */
@Entity
@Table(name = "versao_parametro_operacional")
public class VersaoParametroOperacional extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "parametro_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID parametroId;

    @Column(name = "versao", nullable = false, updatable = false)
    private int versao;

    @Column(name = "valor_anterior", length = 500, updatable = false)
    private String valorAnterior;

    @Column(name = "valor_novo", nullable = false, length = 500, updatable = false)
    private String valorNovo;

    @Column(name = "ator_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID atorId;

    @Column(name = "justificativa", nullable = false, length = 500, updatable = false)
    private String justificativa;

    protected VersaoParametroOperacional() {
        // requerido pelo Hibernate
    }

    private VersaoParametroOperacional(
            UUID id,
            UUID parametroId,
            int versao,
            String valorAnterior,
            String valorNovo,
            UUID atorId,
            String justificativa) {
        this.id = id;
        this.parametroId = parametroId;
        this.versao = versao;
        this.valorAnterior = valorAnterior;
        this.valorNovo = valorNovo;
        this.atorId = atorId;
        this.justificativa = justificativa;
    }

    public static VersaoParametroOperacional registrar(
            UUID parametroId, int versao, String valorAnterior, String valorNovo, UUID atorId, String justificativa) {
        if (justificativa == null || justificativa.isBlank()) {
            throw new IllegalArgumentException("justificativa obrigatoria");
        }
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new VersaoParametroOperacional(id, parametroId, versao, valorAnterior, valorNovo, atorId, justificativa);
    }

    public UUID getId() {
        return id;
    }

    public UUID getParametroId() {
        return parametroId;
    }

    public int getVersao() {
        return versao;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNovo() {
        return valorNovo;
    }

    public UUID getAtorId() {
        return atorId;
    }

    public String getJustificativa() {
        return justificativa;
    }
}

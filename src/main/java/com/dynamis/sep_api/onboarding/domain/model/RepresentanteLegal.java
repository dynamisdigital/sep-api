package com.dynamis.sep_api.onboarding.domain.model;

import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.StatusPldRepresentante;
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
 * Representante legal de uma {@link KybEmpresa}. N:1 com a empresa. Carrega status PLD
 * consolidado para o representante (atualizado pelo PldOrchestrationListener apos
 * consulta nas 4 bases obrigatorias).
 *
 * <p>LGPD: CPF persistido inteiro pra cumprir obrigacao regulatoria (PLD/Art. 16), mas
 * jamais exposto inteiro em logs, audit details ou respostas REST publicas — usar
 * apenas mascara via DTO da camada web.
 */
@Entity
@Table(name = "representante_legal")
public class RepresentanteLegal {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "kyb_empresa_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID kybEmpresaId;

    @Column(name = "nome", nullable = false, length = 255)
    private String nome;

    @Column(name = "cpf", nullable = false, length = 11, updatable = false)
    private String cpf;

    @Column(name = "cargo", length = 60)
    private String cargo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_pld", nullable = false, length = 20)
    private StatusPldRepresentante statusPld;

    @Column(name = "data_registro", nullable = false, updatable = false)
    private OffsetDateTime dataRegistro;

    @Column(name = "data_consulta_pld")
    private OffsetDateTime dataConsultaPld;

    protected RepresentanteLegal() {
        // requerido pelo Hibernate
    }

    private RepresentanteLegal(UUID id, UUID kybEmpresaId, String nome, Cpf cpf, String cargo) {
        this.id = id;
        this.kybEmpresaId = kybEmpresaId;
        this.nome = nome;
        this.cpf = cpf.valor();
        this.cargo = cargo;
        this.statusPld = StatusPldRepresentante.PENDENTE;
        this.dataRegistro = OffsetDateTime.now();
    }

    public static RepresentanteLegal criar(UUID kybEmpresaId, String nome, Cpf cpf, String cargo) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome do representante e obrigatorio");
        }
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new RepresentanteLegal(id, kybEmpresaId, nome, cpf, cargo);
    }

    /** Marca representante como limpo no PLD. */
    public void marcarPldLimpo() {
        this.statusPld = StatusPldRepresentante.LIMPO;
        this.dataConsultaPld = OffsetDateTime.now();
    }

    /** Marca representante com hit em base PLD. */
    public void marcarPldHit() {
        this.statusPld = StatusPldRepresentante.HIT;
        this.dataConsultaPld = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getKybEmpresaId() {
        return kybEmpresaId;
    }

    public String getNome() {
        return nome;
    }

    public String getCpf() {
        return cpf;
    }

    public String getCargo() {
        return cargo;
    }

    public StatusPldRepresentante getStatusPld() {
        return statusPld;
    }

    public OffsetDateTime getDataRegistro() {
        return dataRegistro;
    }

    public OffsetDateTime getDataConsultaPld() {
        return dataConsultaPld;
    }
}

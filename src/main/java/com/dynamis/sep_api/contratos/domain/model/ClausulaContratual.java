package com.dynamis.sep_api.contratos.domain.model;

import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Clausula textual extraida do template renderizado de uma {@link VersaoContrato}. Cada clausula
 * tem {@code ordem} unica dentro da versao, alem de titulo e texto.
 */
@Entity
@Table(name = "clausula_contratual")
public class ClausulaContratual {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "versao_id", nullable = false, updatable = false)
    private VersaoContrato versao;

    @Column(name = "ordem", nullable = false, updatable = false)
    private int ordem;

    @Column(name = "titulo", nullable = false, length = 255, updatable = false)
    private String titulo;

    @Column(name = "texto", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String texto;

    protected ClausulaContratual() {}

    private ClausulaContratual(UUID id, VersaoContrato versao, int ordem, String titulo, String texto) {
        this.id = id;
        this.versao = versao;
        this.ordem = ordem;
        this.titulo = titulo;
        this.texto = texto;
    }

    public static ClausulaContratual criar(VersaoContrato versao, int ordem, String titulo, String texto) {
        Objects.requireNonNull(versao, "versao obrigatoria");
        if (ordem <= 0) {
            throw new IllegalArgumentException("ordem deve ser positiva");
        }
        Objects.requireNonNull(titulo, "titulo obrigatorio");
        Objects.requireNonNull(texto, "texto obrigatorio");
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new ClausulaContratual(id, versao, ordem, titulo, texto);
    }

    public UUID getId() {
        return id;
    }

    public VersaoContrato getVersao() {
        return versao;
    }

    public int getOrdem() {
        return ordem;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getTexto() {
        return texto;
    }
}

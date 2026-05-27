package com.dynamis.sep_api.backoffice.domain.model;

import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Comentario interno vinculado a um {@code ItemFilaOperacional} (Sprint 14 Task 14.1). Imutavel
 * apos persistencia. Quando usado como justificativa de resolucao/ignorar, exige >= 20 caracteres
 * (regra validada nos use cases da Task 14.3).
 */
@Entity
@Table(name = "comentario_interno")
public class ComentarioInterno extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "item_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID itemId;

    @Column(name = "autor_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID autorId;

    @Column(name = "conteudo", nullable = false, length = 10000, updatable = false)
    private String conteudo;

    protected ComentarioInterno() {}

    private ComentarioInterno(UUID id, UUID itemId, UUID autorId, String conteudo) {
        this.id = id;
        this.itemId = itemId;
        this.autorId = autorId;
        this.conteudo = conteudo;
    }

    public static ComentarioInterno registrar(UUID itemId, UUID autorId, String conteudo) {
        Objects.requireNonNull(itemId, "itemId obrigatorio");
        Objects.requireNonNull(autorId, "autorId obrigatorio");
        if (conteudo == null || conteudo.isBlank()) {
            throw new IllegalArgumentException("conteudo obrigatorio");
        }
        if (conteudo.length() > 10000) {
            throw new IllegalArgumentException("conteudo nao pode exceder 10000 caracteres");
        }
        return new ComentarioInterno(Generators.timeBasedReorderedGenerator().generate(), itemId, autorId, conteudo);
    }

    public UUID getId() {
        return id;
    }

    public UUID getItemId() {
        return itemId;
    }

    public UUID getAutorId() {
        return autorId;
    }

    public String getConteudo() {
        return conteudo;
    }

    public OffsetDateTime getDataCriacao() {
        return dataCriacao;
    }
}

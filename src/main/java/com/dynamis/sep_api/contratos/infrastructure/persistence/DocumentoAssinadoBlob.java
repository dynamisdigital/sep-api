package com.dynamis.sep_api.contratos.infrastructure.persistence;

import com.fasterxml.uuid.Generators;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Tabela auxiliar de binarios do PDF assinado (Sprint 11). Existe pra isolar o BYTEA da entidade
 * de dominio {@code DocumentoAssinado} — o dominio carrega apenas o {@code pathStorage} opaco e
 * acessa o blob via port {@code DocumentoAssinadoStorage}.
 *
 * <p>Quando o storage migrar pra S3/MinIO (Epic 16), esta tabela vira tabela morta e o port aponta
 * pro adapter remoto sem alterar o dominio.
 */
@Entity
@Table(name = "documento_assinado_blob")
public class DocumentoAssinadoBlob {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Basic(fetch = FetchType.LAZY)
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "conteudo", nullable = false, updatable = false)
    private byte[] conteudo;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private OffsetDateTime dataCriacao;

    protected DocumentoAssinadoBlob() {}

    private DocumentoAssinadoBlob(UUID id, byte[] conteudo, OffsetDateTime dataCriacao) {
        this.id = id;
        this.conteudo = conteudo;
        this.dataCriacao = dataCriacao;
    }

    public static DocumentoAssinadoBlob criar(byte[] conteudo) {
        Objects.requireNonNull(conteudo, "conteudo obrigatorio");
        if (conteudo.length == 0) {
            throw new IllegalArgumentException("conteudo do PDF assinado nao pode ser vazio");
        }
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new DocumentoAssinadoBlob(id, conteudo, OffsetDateTime.now());
    }

    public UUID getId() {
        return id;
    }

    public byte[] getConteudo() {
        return conteudo;
    }

    public OffsetDateTime getDataCriacao() {
        return dataCriacao;
    }
}

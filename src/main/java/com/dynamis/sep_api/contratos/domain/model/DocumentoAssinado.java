package com.dynamis.sep_api.contratos.domain.model;

import com.dynamis.sep_api.contratos.domain.vo.HashValidator;
import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
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
 * PDF assinado retornado pelo provider de assinatura (Sprint 11). 1:1 com {@link
 * EnvelopeAssinatura} finalizado em {@code ASSINADO}.
 *
 * <p>Armazenamento inline em coluna {@code conteudo BYTEA} (mesma estrategia do {@code
 * DocumentoCadastral} da Sprint 6). Roadmap Epic 16: migrar para S3/MinIO mantendo apenas
 * referencia + metadados no banco. Retencao minima de 10 anos (CMN 4.656/2018 + LGPD).
 *
 * <p>{@code hashSha256} e calculado pelo SEP sobre o PDF assinado baixado do provider, garantindo
 * integridade local independente de carimbo do provider.
 */
@Entity
@Table(name = "documento_assinado")
public class DocumentoAssinado extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "envelope_id", columnDefinition = "uuid", nullable = false, updatable = false, unique = true)
    private UUID envelopeId;

    @Column(name = "hash_sha256", nullable = false, length = 64, updatable = false)
    private String hashSha256;

    @Column(name = "data_assinatura", nullable = false, updatable = false)
    private OffsetDateTime dataAssinatura;

    @Column(name = "selo", length = 500, updatable = false)
    private String selo;

    @Basic(fetch = FetchType.LAZY)
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "conteudo", nullable = false, updatable = false)
    private byte[] conteudo;

    protected DocumentoAssinado() {}

    private DocumentoAssinado(
            UUID id, UUID envelopeId, String hashSha256, OffsetDateTime dataAssinatura, String selo, byte[] conteudo) {
        this.id = id;
        this.envelopeId = envelopeId;
        this.hashSha256 = hashSha256;
        this.dataAssinatura = dataAssinatura;
        this.selo = selo;
        this.conteudo = conteudo;
    }

    public static DocumentoAssinado criar(
            UUID envelopeId, String hashSha256, OffsetDateTime dataAssinatura, String selo, byte[] conteudo) {
        Objects.requireNonNull(envelopeId, "envelopeId obrigatorio");
        Objects.requireNonNull(dataAssinatura, "dataAssinatura obrigatoria");
        Objects.requireNonNull(conteudo, "conteudo obrigatorio");
        if (conteudo.length == 0) {
            throw new IllegalArgumentException("conteudo do PDF assinado nao pode ser vazio");
        }
        HashValidator.requireValid(hashSha256, "hashSha256");
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new DocumentoAssinado(id, envelopeId, hashSha256, dataAssinatura, selo, conteudo);
    }

    public UUID getId() {
        return id;
    }

    public UUID getEnvelopeId() {
        return envelopeId;
    }

    public String getHashSha256() {
        return hashSha256;
    }

    public OffsetDateTime getDataAssinatura() {
        return dataAssinatura;
    }

    public String getSelo() {
        return selo;
    }

    public byte[] getConteudo() {
        return conteudo;
    }
}

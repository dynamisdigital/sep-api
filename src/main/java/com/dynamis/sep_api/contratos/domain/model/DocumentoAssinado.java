package com.dynamis.sep_api.contratos.domain.model;

import com.dynamis.sep_api.contratos.domain.vo.HashValidator;
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
 * Metadados do PDF assinado retornado pelo provider de assinatura (Sprint 11). 1:1 com {@link
 * EnvelopeAssinatura} finalizado em {@code ASSINADO}.
 *
 * <p>O binario do PDF nao vive nesta entidade — ele e persistido via port {@code
 * DocumentoAssinadoStorage} (Provider Pattern; impl atual e inline em coluna BYTEA, roadmap Epic
 * 16 troca por S3/MinIO sem alterar o dominio). {@code pathStorage} e referencia opaca usada
 * pelo adapter pra recuperar os bytes. Retencao minima de 10 anos (CMN 4.656/2018 + Lei
 * 10.931/2004 + LGPD).
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

    @Column(name = "path_storage", nullable = false, length = 255, updatable = false)
    private String pathStorage;

    protected DocumentoAssinado() {}

    private DocumentoAssinado(
            UUID id,
            UUID envelopeId,
            String hashSha256,
            OffsetDateTime dataAssinatura,
            String selo,
            String pathStorage) {
        this.id = id;
        this.envelopeId = envelopeId;
        this.hashSha256 = hashSha256;
        this.dataAssinatura = dataAssinatura;
        this.selo = selo;
        this.pathStorage = pathStorage;
    }

    public static DocumentoAssinado criar(
            UUID envelopeId, String hashSha256, OffsetDateTime dataAssinatura, String selo, String pathStorage) {
        Objects.requireNonNull(envelopeId, "envelopeId obrigatorio");
        Objects.requireNonNull(hashSha256, "hashSha256 obrigatorio");
        Objects.requireNonNull(dataAssinatura, "dataAssinatura obrigatoria");
        Objects.requireNonNull(pathStorage, "pathStorage obrigatorio");
        if (pathStorage.isBlank()) {
            throw new IllegalArgumentException("pathStorage nao pode ser em branco");
        }
        HashValidator.requireValid(hashSha256, "hashSha256");
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new DocumentoAssinado(id, envelopeId, hashSha256, dataAssinatura, selo, pathStorage);
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

    public String getPathStorage() {
        return pathStorage;
    }
}

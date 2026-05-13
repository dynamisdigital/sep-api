package com.dynamis.sep_api.onboarding.domain.model;

import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;
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
 * Documento cadastral anexado a uma solicitacao de onboarding. Persistido com binario inline em
 * BYTEA (storage temporario; sera migrado para S3/MinIO em Epic 16).
 *
 * <p>LGPD: nunca logar {@link #conteudo}. Logar apenas {@link #sha256}, {@link #mimeType} e
 * {@link #tamanhoBytes}.
 */
@Entity
@Table(name = "documento_cadastral")
public class DocumentoCadastral {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "solicitacao_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID solicitacaoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoDocumento tipo;

    @Column(name = "conteudo", nullable = false)
    private byte[] conteudo;

    @Column(name = "mime_type", nullable = false, length = 50)
    private String mimeType;

    @Column(name = "nome_original", nullable = false, length = 255)
    private String nomeOriginal;

    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "data_envio", nullable = false, updatable = false)
    private OffsetDateTime dataEnvio;

    protected DocumentoCadastral() {
        // requerido pelo Hibernate
    }

    private DocumentoCadastral(
            UUID id,
            UUID solicitacaoId,
            TipoDocumento tipo,
            byte[] conteudo,
            String mimeType,
            String nomeOriginal,
            long tamanhoBytes,
            String sha256) {
        this.id = id;
        this.solicitacaoId = solicitacaoId;
        this.tipo = tipo;
        this.conteudo = conteudo;
        this.mimeType = mimeType;
        this.nomeOriginal = nomeOriginal;
        this.tamanhoBytes = tamanhoBytes;
        this.sha256 = sha256;
        this.dataEnvio = OffsetDateTime.now();
    }

    public static DocumentoCadastral criar(
            UUID solicitacaoId,
            TipoDocumento tipo,
            byte[] conteudo,
            String mimeType,
            String nomeOriginal,
            String sha256) {
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new DocumentoCadastral(
                id, solicitacaoId, tipo, conteudo, mimeType, nomeOriginal, conteudo.length, sha256);
    }

    public UUID getId() {
        return id;
    }

    public UUID getSolicitacaoId() {
        return solicitacaoId;
    }

    public TipoDocumento getTipo() {
        return tipo;
    }

    public byte[] getConteudo() {
        return conteudo;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getNomeOriginal() {
        return nomeOriginal;
    }

    public long getTamanhoBytes() {
        return tamanhoBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public OffsetDateTime getDataEnvio() {
        return dataEnvio;
    }
}

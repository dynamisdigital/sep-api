package com.dynamis.sep_api.contratos.domain.model;

import com.dynamis.sep_api.contratos.domain.exception.ContratoEstadoInvalidoException;
import com.dynamis.sep_api.contratos.domain.vo.StatusEnvelope;
import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Envelope de assinatura digital (Sprint 11). Vincula uma {@link VersaoContrato} a um provider
 * externo via {@code idEnvelopeExterno} e materializa o ciclo de vida do envelope conforme {@link
 * StatusEnvelope}.
 *
 * <p>Idempotencia de envio: chave {@code idempotencyKey} unica derivada de {@code contratoId +
 * numeroVersao}. Reenvio com mesma chave devolve o envelope existente sem criar novo.
 *
 * <p>1 envelope ativo por versao de contrato nesta sprint (unique em {@code versao_id}).
 */
@Entity
@Table(name = "envelope_assinatura")
public class EnvelopeAssinatura extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "contrato_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID contratoId;

    @Column(name = "versao_id", columnDefinition = "uuid", nullable = false, updatable = false, unique = true)
    private UUID versaoId;

    @Column(name = "provider", nullable = false, length = 40, updatable = false)
    private String provider;

    @Column(name = "id_envelope_externo", length = 100)
    private String idEnvelopeExterno;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StatusEnvelope status;

    @Column(name = "hash_pdf_enviado", nullable = false, length = 64, updatable = false)
    private String hashPdfEnviado;

    @Column(name = "data_envio")
    private OffsetDateTime dataEnvio;

    @Column(name = "data_atualizacao_provider")
    private OffsetDateTime dataAtualizacaoProvider;

    protected EnvelopeAssinatura() {}

    private EnvelopeAssinatura(
            UUID id, UUID contratoId, UUID versaoId, String provider, String idempotencyKey, String hashPdfEnviado) {
        this.id = id;
        this.contratoId = contratoId;
        this.versaoId = versaoId;
        this.provider = provider;
        this.idempotencyKey = idempotencyKey;
        this.hashPdfEnviado = hashPdfEnviado;
        this.status = StatusEnvelope.RASCUNHO;
    }

    public static EnvelopeAssinatura criar(
            UUID contratoId, UUID versaoId, String provider, String idempotencyKey, String hashPdfEnviado) {
        Objects.requireNonNull(contratoId, "contratoId obrigatorio");
        Objects.requireNonNull(versaoId, "versaoId obrigatoria");
        Objects.requireNonNull(provider, "provider obrigatorio");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey obrigatorio");
        Objects.requireNonNull(hashPdfEnviado, "hashPdfEnviado obrigatorio");
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new EnvelopeAssinatura(id, contratoId, versaoId, provider, idempotencyKey, hashPdfEnviado);
    }

    /** Marca envelope como {@code ENVIADO} apos provider aceitar. */
    public void marcarEnviado(String idEnvelopeExterno, OffsetDateTime dataEnvio) {
        Objects.requireNonNull(idEnvelopeExterno, "idEnvelopeExterno obrigatorio");
        if (status != StatusEnvelope.RASCUNHO) {
            throw new ContratoEstadoInvalidoException("marcarEnviado", null);
        }
        this.idEnvelopeExterno = idEnvelopeExterno;
        this.status = StatusEnvelope.ENVIADO;
        this.dataEnvio = dataEnvio;
        this.dataAtualizacaoProvider = dataEnvio;
    }

    /** Transicao de auditoria; nao finaliza envelope. */
    public void marcarVisualizado(OffsetDateTime quando) {
        if (status.isFinal()) {
            return;
        }
        if (status == StatusEnvelope.ENVIADO) {
            this.status = StatusEnvelope.VISUALIZADO;
        }
        this.dataAtualizacaoProvider = quando;
    }

    public void marcarAssinado(OffsetDateTime quando) {
        if (status == StatusEnvelope.ASSINADO) {
            return;
        }
        if (status.isFinal()) {
            throw new ContratoEstadoInvalidoException("marcarAssinado", null);
        }
        this.status = StatusEnvelope.ASSINADO;
        this.dataAtualizacaoProvider = quando;
    }

    public void marcarRecusado(OffsetDateTime quando) {
        if (status == StatusEnvelope.RECUSADO) {
            return;
        }
        if (status.isFinal()) {
            throw new ContratoEstadoInvalidoException("marcarRecusado", null);
        }
        this.status = StatusEnvelope.RECUSADO;
        this.dataAtualizacaoProvider = quando;
    }

    public void marcarExpirado(OffsetDateTime quando) {
        if (status == StatusEnvelope.EXPIRADO) {
            return;
        }
        if (status.isFinal()) {
            throw new ContratoEstadoInvalidoException("marcarExpirado", null);
        }
        this.status = StatusEnvelope.EXPIRADO;
        this.dataAtualizacaoProvider = quando;
    }

    public UUID getId() {
        return id;
    }

    public UUID getContratoId() {
        return contratoId;
    }

    public UUID getVersaoId() {
        return versaoId;
    }

    public String getProvider() {
        return provider;
    }

    public String getIdEnvelopeExterno() {
        return idEnvelopeExterno;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public StatusEnvelope getStatus() {
        return status;
    }

    public String getHashPdfEnviado() {
        return hashPdfEnviado;
    }

    public OffsetDateTime getDataEnvio() {
        return dataEnvio;
    }

    public OffsetDateTime getDataAtualizacaoProvider() {
        return dataAtualizacaoProvider;
    }
}

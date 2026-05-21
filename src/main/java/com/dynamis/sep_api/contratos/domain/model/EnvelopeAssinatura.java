package com.dynamis.sep_api.contratos.domain.model;

import com.dynamis.sep_api.contratos.domain.exception.ContratoEstadoInvalidoException;
import com.dynamis.sep_api.contratos.domain.vo.HashValidator;
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
 * <p>Invariante: {@code EnvelopeAssinatura} so e persistido APOS o provider externo ja ter aceito
 * o documento e devolvido {@code idEnvelopeExterno}. Por isso o factory {@link
 * #criar(UUID, UUID, String, String, String, String, OffsetDateTime)} exige {@code
 * idEnvelopeExterno} e {@code dataEnvio} e nasce diretamente em {@link StatusEnvelope#ENVIADO}.
 * O estado {@code RASCUNHO} existe no enum apenas como reserva para fluxos futuros (ex.: envio
 * em duas fases) — nesta sprint nao e alcancavel via API publica.
 *
 * <p>Idempotencia de envio: chave {@code idempotencyKey} unica derivada de {@code contratoId +
 * numeroVersao}. Reenvio com mesma chave devolve o envelope existente sem criar novo (regra
 * aplicada no use case, garantida no banco pelo unique constraint).
 *
 * <p>1 envelope ativo por versao de contrato nesta sprint (unique em {@code versao_id}).
 *
 * <p>Atencao ao nome {@link StatusEnvelope#RECUSADO}: nao confundir com {@link
 * com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao#RECUSADO}. Ambos significam "recusa
 * de assinatura" mas operam em VOs distintos (ciclo do envelope vs. ciclo do contrato). Audit log
 * usa tipos distintos ({@code ASSINATURA_RECUSADA} vs. {@code CONTRATO_RECUSADO} — Task 11.8).
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

    @Column(name = "id_envelope_externo", nullable = false, length = 100, updatable = false)
    private String idEnvelopeExterno;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StatusEnvelope status;

    @Column(name = "hash_pdf_enviado", nullable = false, length = 64, updatable = false)
    private String hashPdfEnviado;

    @Column(name = "data_envio", nullable = false, updatable = false)
    private OffsetDateTime dataEnvio;

    @Column(name = "data_atualizacao_provider", nullable = false)
    private OffsetDateTime dataAtualizacaoProvider;

    protected EnvelopeAssinatura() {}

    private EnvelopeAssinatura(
            UUID id,
            UUID contratoId,
            UUID versaoId,
            String provider,
            String idEnvelopeExterno,
            String idempotencyKey,
            String hashPdfEnviado,
            OffsetDateTime dataEnvio) {
        this.id = id;
        this.contratoId = contratoId;
        this.versaoId = versaoId;
        this.provider = provider;
        this.idEnvelopeExterno = idEnvelopeExterno;
        this.idempotencyKey = idempotencyKey;
        this.hashPdfEnviado = hashPdfEnviado;
        this.dataEnvio = dataEnvio;
        this.dataAtualizacaoProvider = dataEnvio;
        this.status = StatusEnvelope.ENVIADO;
    }

    /**
     * Cria envelope APOS provider externo confirmar recebimento do documento. Nasce {@link
     * StatusEnvelope#ENVIADO} com {@code idEnvelopeExterno} e {@code dataEnvio} populados — nao ha
     * fluxo valido que persista {@code RASCUNHO}.
     */
    public static EnvelopeAssinatura criar(
            UUID contratoId,
            UUID versaoId,
            String provider,
            String idEnvelopeExterno,
            String idempotencyKey,
            String hashPdfEnviado,
            OffsetDateTime dataEnvio) {
        Objects.requireNonNull(contratoId, "contratoId obrigatorio");
        Objects.requireNonNull(versaoId, "versaoId obrigatoria");
        Objects.requireNonNull(provider, "provider obrigatorio");
        Objects.requireNonNull(idEnvelopeExterno, "idEnvelopeExterno obrigatorio");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey obrigatorio");
        Objects.requireNonNull(dataEnvio, "dataEnvio obrigatoria");
        HashValidator.requireValid(hashPdfEnviado, "hashPdfEnviado");
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new EnvelopeAssinatura(
                id, contratoId, versaoId, provider, idEnvelopeExterno, idempotencyKey, hashPdfEnviado, dataEnvio);
    }

    /**
     * Marca envelope como visualizado pelo signatario. Idempotente — multiplas chamadas mantem
     * estado {@code VISUALIZADO}. Atualiza {@code dataAtualizacaoProvider} a cada chamada para
     * preservar timestamp do ultimo evento recebido. No-op em estados finais (envelope ja
     * concluido nao volta a ser "visualizado"). Rejeita {@code RASCUNHO}: callback de visualizacao
     * pressupoe envio previo; caso futuro fluxo de duas fases comece a persistir RASCUNHO, o
     * envio precisa preceder o callback.
     */
    public void marcarVisualizado(OffsetDateTime quando) {
        Objects.requireNonNull(quando, "quando obrigatorio");
        if (status == StatusEnvelope.RASCUNHO) {
            throw new ContratoEstadoInvalidoException("marcarVisualizado", null);
        }
        if (status.isFinal()) {
            return;
        }
        if (status == StatusEnvelope.ENVIADO) {
            this.status = StatusEnvelope.VISUALIZADO;
        }
        this.dataAtualizacaoProvider = quando;
    }

    public void marcarAssinado(OffsetDateTime quando) {
        Objects.requireNonNull(quando, "quando obrigatorio");
        if (status == StatusEnvelope.ASSINADO) {
            this.dataAtualizacaoProvider = quando;
            return;
        }
        if (status.isFinal()) {
            throw new ContratoEstadoInvalidoException("marcarAssinado", null);
        }
        this.status = StatusEnvelope.ASSINADO;
        this.dataAtualizacaoProvider = quando;
    }

    public void marcarRecusado(OffsetDateTime quando) {
        Objects.requireNonNull(quando, "quando obrigatorio");
        if (status == StatusEnvelope.RECUSADO) {
            this.dataAtualizacaoProvider = quando;
            return;
        }
        if (status.isFinal()) {
            throw new ContratoEstadoInvalidoException("marcarRecusado", null);
        }
        this.status = StatusEnvelope.RECUSADO;
        this.dataAtualizacaoProvider = quando;
    }

    public void marcarExpirado(OffsetDateTime quando) {
        Objects.requireNonNull(quando, "quando obrigatorio");
        if (status == StatusEnvelope.EXPIRADO) {
            this.dataAtualizacaoProvider = quando;
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

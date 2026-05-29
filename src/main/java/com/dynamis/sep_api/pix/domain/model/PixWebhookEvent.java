package com.dynamis.sep_api.pix.domain.model;

import com.dynamis.sep_api.pix.domain.vo.StatusPixWebhookEvent;
import com.dynamis.sep_api.pix.domain.vo.TipoPixWebhookEvent;
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
import java.util.regex.Pattern;

/**
 * Snapshot operacional de um evento de webhook Pix recebido (Epic 15 / Sprint 19).
 *
 * <p>Por minimizacao de dados sensiveis, o payload bruto <strong>nao</strong> e persistido: guarda-se
 * apenas {@code payloadHash}, ids, tipo e status de processamento. Idempotencia garantida pela
 * unicidade de {@code (provider, event_id)} (V45).
 */
@Entity
@Table(name = "pix_webhook_event")
public class PixWebhookEvent extends EntidadeAuditavel {

    /** SHA-256 em hex: exatamente 64 caracteres hexadecimais (minimizacao de dados sensiveis). */
    private static final Pattern SHA256_HEX = Pattern.compile("[a-fA-F0-9]{64}");

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "event_id", nullable = false, length = 120)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private TipoPixWebhookEvent eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusPixWebhookEvent status;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "erro")
    private String erro;

    protected PixWebhookEvent() {
        // JPA
    }

    private PixWebhookEvent(
            UUID id,
            String provider,
            String eventId,
            TipoPixWebhookEvent eventType,
            StatusPixWebhookEvent status,
            String payloadHash) {
        this.id = id;
        this.provider = provider;
        this.eventId = eventId;
        this.eventType = eventType;
        this.status = status;
        this.payloadHash = payloadHash;
        this.receivedAt = OffsetDateTime.now();
    }

    /** Registra um evento recebido em {@link StatusPixWebhookEvent#RECEBIDO}, antes do processamento. */
    public static PixWebhookEvent receber(
            String provider, String eventId, TipoPixWebhookEvent eventType, String payloadHash) {
        exigirTexto(provider, "provider");
        exigirTexto(eventId, "eventId");
        exigirTexto(payloadHash, "payloadHash");
        if (!SHA256_HEX.matcher(payloadHash).matches()) {
            throw new IllegalArgumentException("payloadHash deve ser SHA-256 hex (64 caracteres)");
        }
        Objects.requireNonNull(eventType, "eventType obrigatorio");
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new PixWebhookEvent(id, provider, eventId, eventType, StatusPixWebhookEvent.RECEBIDO, payloadHash);
    }

    /**
     * Marca como processado com sucesso. Idempotente: repeticao mantem o estado. So permitido a
     * partir de {@link StatusPixWebhookEvent#RECEBIDO} — estados IGNORADO/FALHOU nao reprocessam aqui.
     */
    public void marcarProcessado() {
        if (this.status == StatusPixWebhookEvent.PROCESSADO) {
            return;
        }
        exigirEstado(StatusPixWebhookEvent.RECEBIDO);
        this.status = StatusPixWebhookEvent.PROCESSADO;
        this.erro = null;
    }

    /** Marca como ignorado (evento desconhecido), registrando o motivo tecnico. */
    public void marcarIgnorado(String motivo) {
        exigirEstado(StatusPixWebhookEvent.RECEBIDO);
        this.status = StatusPixWebhookEvent.IGNORADO;
        this.erro = motivo;
    }

    /** Marca falha de processamento, registrando a causa sanitizada para reprocesso futuro. */
    public void marcarFalhou(String erro) {
        exigirEstado(StatusPixWebhookEvent.RECEBIDO);
        this.status = StatusPixWebhookEvent.FALHOU;
        this.erro = erro;
    }

    private void exigirEstado(StatusPixWebhookEvent... permitidos) {
        for (StatusPixWebhookEvent permitido : permitidos) {
            if (this.status == permitido) {
                return;
            }
        }
        throw new IllegalStateException("transicao invalida a partir de " + this.status);
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " obrigatorio");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getEventId() {
        return eventId;
    }

    public TipoPixWebhookEvent getEventType() {
        return eventType;
    }

    public StatusPixWebhookEvent getStatus() {
        return status;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public String getErro() {
        return erro;
    }
}

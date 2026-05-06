package com.dynamis.sep_api.shared.domain.model;

import com.dynamis.sep_api.shared.audit.EntidadeAuditavel;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro outbox de webhook recebido. Sprint 4 apenas grava o evento como {@link
 * WebhookEventStatus#PENDENTE}; o processamento assincrono entra nas Epics 5 (KYC) e 15 (Pix).
 *
 * <p>Garantia de idempotencia: {@code idempotency_key} e unico no banco (V3).
 */
@Entity
@Table(name = "webhook_event_log")
public class WebhookEventLog extends EntidadeAuditavel {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "event", nullable = false, length = 100)
    private String event;

    @Column(name = "idempotency_key", nullable = false, length = 120, unique = true)
    private String idempotencyKey;

    @Column(name = "signature", length = 512)
    private String signature;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WebhookEventStatus status;

    @Column(name = "erro")
    private String erro;

    @Column(name = "data_recebimento", nullable = false)
    private OffsetDateTime dataRecebimento;

    @Column(name = "data_processamento")
    private OffsetDateTime dataProcessamento;

    protected WebhookEventLog() {
        // Hibernate
    }

    private WebhookEventLog(
            UUID id,
            String provider,
            String event,
            String idempotencyKey,
            String signature,
            String payload,
            WebhookEventStatus status,
            OffsetDateTime dataRecebimento) {
        this.id = id;
        this.provider = provider;
        this.event = event;
        this.idempotencyKey = idempotencyKey;
        this.signature = signature;
        this.payload = payload;
        this.status = status;
        this.dataRecebimento = dataRecebimento;
    }

    public static WebhookEventLog registrar(
            String provider, String event, String idempotencyKey, String signature, String payload) {
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        return new WebhookEventLog(
                id,
                provider,
                event,
                idempotencyKey,
                signature,
                payload,
                WebhookEventStatus.PENDENTE,
                OffsetDateTime.now());
    }

    public UUID getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getEvent() {
        return event;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getSignature() {
        return signature;
    }

    public String getPayload() {
        return payload;
    }

    public WebhookEventStatus getStatus() {
        return status;
    }

    public String getErro() {
        return erro;
    }

    public OffsetDateTime getDataRecebimento() {
        return dataRecebimento;
    }

    public OffsetDateTime getDataProcessamento() {
        return dataProcessamento;
    }
}

package com.dynamis.sep_api.contratos.domain.model;

import com.dynamis.sep_api.contratos.domain.vo.StatusEnvelope;
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
 * Historico de eventos de assinatura recebidos do provider (Sprint 11). Cada callback do provider
 * vira um {@code EventoAssinatura} sanitizado. Unique em {@code (envelope_id, id_evento_externo)}
 * garante idempotencia.
 *
 * <p>NAO persistir payload bruto completo se contiver PII desnecessaria; salvar apenas
 * {@code payloadResumo} truncado (max 1000 chars).
 */
@Entity
@Table(name = "evento_assinatura")
public class EventoAssinatura {

    public static final int PAYLOAD_RESUMO_MAX = 1000;

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "envelope_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID envelopeId;

    @Column(name = "id_evento_externo", nullable = false, length = 100, updatable = false)
    private String idEventoExterno;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40, updatable = false)
    private StatusEnvelope status;

    @Column(name = "payload_resumo", length = PAYLOAD_RESUMO_MAX, updatable = false)
    private String payloadResumo;

    @Column(name = "data_evento", nullable = false, updatable = false)
    private OffsetDateTime dataEvento;

    protected EventoAssinatura() {}

    private EventoAssinatura(
            UUID id,
            UUID envelopeId,
            String idEventoExterno,
            StatusEnvelope status,
            String payloadResumo,
            OffsetDateTime dataEvento) {
        this.id = id;
        this.envelopeId = envelopeId;
        this.idEventoExterno = idEventoExterno;
        this.status = status;
        this.payloadResumo = payloadResumo;
        this.dataEvento = dataEvento;
    }

    public static EventoAssinatura criar(
            UUID envelopeId,
            String idEventoExterno,
            StatusEnvelope status,
            String payloadResumo,
            OffsetDateTime dataEvento) {
        Objects.requireNonNull(envelopeId, "envelopeId obrigatorio");
        Objects.requireNonNull(idEventoExterno, "idEventoExterno obrigatorio");
        Objects.requireNonNull(status, "status obrigatorio");
        Objects.requireNonNull(dataEvento, "dataEvento obrigatoria");
        UUID id = Generators.timeBasedReorderedGenerator().generate();
        String resumo = truncar(payloadResumo);
        return new EventoAssinatura(id, envelopeId, idEventoExterno, status, resumo, dataEvento);
    }

    private static String truncar(String valor) {
        if (valor == null) {
            return null;
        }
        return valor.length() > PAYLOAD_RESUMO_MAX ? valor.substring(0, PAYLOAD_RESUMO_MAX) : valor;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEnvelopeId() {
        return envelopeId;
    }

    public String getIdEventoExterno() {
        return idEventoExterno;
    }

    public StatusEnvelope getStatus() {
        return status;
    }

    public String getPayloadResumo() {
        return payloadResumo;
    }

    public OffsetDateTime getDataEvento() {
        return dataEvento;
    }
}

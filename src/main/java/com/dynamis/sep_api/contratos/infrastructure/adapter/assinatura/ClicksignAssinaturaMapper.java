package com.dynamis.sep_api.contratos.infrastructure.adapter.assinatura;

import com.dynamis.sep_api.contratos.domain.vo.StatusEnvelope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Mapper Clicksign -> dominio (Sprint 11 Task 11.4). Traduz o vocabulario nativo da Clicksign
 * (running/closed/refused/canceled) para {@link StatusEnvelope}.
 *
 * <p>Sem MapStruct aqui — o mapping e pequeno e baseado em switch sobre String do provider. Usar
 * {@code @Mapper} pra string -> enum custaria mais leitura que ganho.
 */
@Component
public class ClicksignAssinaturaMapper {

    public StatusEnvelope toStatusEnvelope(String clicksignStatus) {
        if (clicksignStatus == null) {
            return StatusEnvelope.ENVIADO;
        }
        return switch (clicksignStatus.toLowerCase()) {
            case "running" -> StatusEnvelope.ENVIADO;
            case "viewed" -> StatusEnvelope.VISUALIZADO;
            case "closed", "signed", "finished" -> StatusEnvelope.ASSINADO;
            case "refused", "rejected" -> StatusEnvelope.RECUSADO;
            case "expired" -> StatusEnvelope.EXPIRADO;
            case "canceled", "cancelled" -> StatusEnvelope.RECUSADO;
            default -> StatusEnvelope.ENVIADO;
        };
    }

    public OffsetDateTime parseUpdatedAt(String updatedAt) {
        if (updatedAt == null || updatedAt.isBlank()) {
            return OffsetDateTime.now();
        }
        try {
            return OffsetDateTime.parse(updatedAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException e) {
            return OffsetDateTime.now();
        }
    }
}

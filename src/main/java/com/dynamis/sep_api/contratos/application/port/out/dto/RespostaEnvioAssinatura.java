package com.dynamis.sep_api.contratos.application.port.out.dto;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Resposta minima exigida pelo dominio apos envio bem-sucedido ao provider (Sprint 11 Task 11.4).
 * {@code idEnvelopeExterno} e obrigatorio — ele e a chave de correlacao entre dominio SEP e
 * provider externo (usada em callback/webhook + lookup).
 */
public record RespostaEnvioAssinatura(String idEnvelopeExterno, OffsetDateTime dataEnvio) {

    public RespostaEnvioAssinatura {
        Objects.requireNonNull(idEnvelopeExterno, "idEnvelopeExterno obrigatorio");
        Objects.requireNonNull(dataEnvio, "dataEnvio obrigatoria");
        if (idEnvelopeExterno.isBlank()) {
            throw new IllegalArgumentException("idEnvelopeExterno nao pode ser em branco");
        }
    }
}

package com.dynamis.sep_api.contratos.application.port.out.dto;

import com.dynamis.sep_api.contratos.domain.vo.StatusEnvelope;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Snapshot do status remoto do envelope retornado pelo provider (Sprint 11 Task 11.4). DTO em
 * linguagem de dominio — o adapter ja traduziu o vocabulario nativo do provider para {@link
 * StatusEnvelope}.
 */
public record StatusEnvelopeProvider(StatusEnvelope status, OffsetDateTime dataAtualizacao) {

    public StatusEnvelopeProvider {
        Objects.requireNonNull(status, "status obrigatorio");
        Objects.requireNonNull(dataAtualizacao, "dataAtualizacao obrigatoria");
    }
}

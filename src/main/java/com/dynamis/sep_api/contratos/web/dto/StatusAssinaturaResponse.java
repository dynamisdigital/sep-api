package com.dynamis.sep_api.contratos.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Snapshot do ciclo de assinatura digital do contrato (Sprint 11 Task 11.7).
 *
 * <p>{@code statusEnvelope} e {@code idEnvelopeExterno} sao {@code null} enquanto nenhum envelope
 * tiver sido criado (contrato ainda em {@code AGUARDANDO_ACEITE}/{@code ACEITO} antes do
 * disparo automatico pelo {@code ContratoAceitoListener}).
 */
@Schema(description = "Status agregado da assinatura digital de um contrato")
public record StatusAssinaturaResponse(
        @Schema(description = "Status do contrato no fluxo de formalizacao", example = "EM_ASSINATURA")
                String statusContrato,
        @Schema(description = "Status do envelope no provider (null se nao houver envelope)", example = "ENVIADO")
                String statusEnvelope,
        @Schema(description = "Identificador do envelope no provider externo (null se nao houver envelope)")
                String idEnvelopeExterno,
        @Schema(description = "Ultima atualizacao recebida do provider (null se nao houver envelope)")
                OffsetDateTime dataAtualizacaoProvider) {}

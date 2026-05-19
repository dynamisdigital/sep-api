package com.dynamis.sep_api.credito.web.dto;

import com.dynamis.sep_api.credito.domain.vo.StatusConsentimento;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Resposta do {@code GET .../open-finance} (Sprint 9 Task 9.6). Estado atual do consentimento +
 * snapshot consolidado (quando AUTORIZADO + dados recebidos).
 */
@Schema(description = "Status Open Finance de uma proposta — consentimento + ultima movimentacao consolidada")
public record OpenFinanceStatusResponse(
        StatusConsentimento statusConsentimento,
        OffsetDateTime dataInicio,
        OffsetDateTime dataAutorizacao,
        OffsetDateTime dataExpiracao,
        @Schema(description = "Snapshot mais recente; null se autorizacao ainda nao recebeu dados")
                MovimentacaoConsolidadaResponse ultimaMovimentacao) {}

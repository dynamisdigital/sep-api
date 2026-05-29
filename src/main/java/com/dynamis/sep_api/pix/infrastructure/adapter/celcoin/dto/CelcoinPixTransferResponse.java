package com.dynamis.sep_api.pix.infrastructure.adapter.celcoin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resposta da Celcoin para solicitacao/consulta de transferencia Pix. {@code status} e o status cru
 * do provider, mapeado para {@code StatusTransferenciaPixProvider} pelo adapter.
 */
public record CelcoinPixTransferResponse(
        @JsonProperty("transfer_id") String transferId, @JsonProperty("status") String status) {}

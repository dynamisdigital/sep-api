package com.dynamis.sep_api.pix.application.port.out.dto;

/**
 * Status reportado pelo provider Pix para uma transferencia. Distinto de {@code
 * StatusPixTransferencia} (ciclo de vida do dominio, que inclui estados locais como CRIADA e
 * CANCELADA que o provider nunca devolve). O adapter mapeia o status cru Celcoin para este enum.
 */
public enum StatusTransferenciaPixProvider {
    PENDENTE,
    PROCESSANDO,
    CONCLUIDA,
    REJEITADA
}

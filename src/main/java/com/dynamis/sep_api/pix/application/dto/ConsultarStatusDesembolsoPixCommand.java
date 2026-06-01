package com.dynamis.sep_api.pix.application.dto;

import java.util.UUID;

/**
 * Comando de consulta de status de um desembolso Pix (Sprint 20 Task 20.3).
 *
 * @param reconsultarProvider {@code true} reconcilia consultando o provider externo (endpoint POST
 *     {@code /status} + reprocesso). {@code false} faz apenas leitura local (GET).
 */
public record ConsultarStatusDesembolsoPixCommand(
        UUID transferenciaId, String correlationId, boolean reconsultarProvider) {}

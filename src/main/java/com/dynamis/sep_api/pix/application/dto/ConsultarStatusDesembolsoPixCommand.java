package com.dynamis.sep_api.pix.application.dto;

import java.util.UUID;

/** Comando de consulta de status de um desembolso Pix (Sprint 20 Task 20.3). */
public record ConsultarStatusDesembolsoPixCommand(UUID transferenciaId, String correlationId) {}

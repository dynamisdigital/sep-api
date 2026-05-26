package com.dynamis.sep_api.backoffice.application.port.out;

import com.dynamis.sep_api.backoffice.application.port.out.dto.ResultadoReprocesso;

import java.util.UUID;

/** Porta de saida (Sprint 14 Task 14.4) — re-dispara processamento de evento em {@code webhook_event_log}. */
public interface WebhookReprocessadorPort {

    ResultadoReprocesso reprocessar(UUID webhookEventId);
}

package com.dynamis.sep_api.pix.application.port.out;

import com.dynamis.sep_api.pix.application.port.out.dto.ComandoTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.EventoWebhookPixNormalizado;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaTransferenciaPix;

/**
 * Port de saida para o provedor Pix (Provider Pattern, ADR 0004; Epic 15 / Sprint 19).
 *
 * <ul>
 *   <li>{@code FakePixProvider} — dev/test sem credenciais Celcoin; cenarios deterministicos.
 *   <li>{@code CelcoinPixProvider} — adapter HTTP real com OAuth2 + Resilience4j (skeleton nesta
 *       sprint; desembolso real fica para Sprints 20/21).
 * </ul>
 *
 * <p>Selecao por {@code app.pix.provider} ({@code fake} ou {@code celcoin}). DTOs falam linguagem
 * de dominio SEP — request/response Celcoin vivem apenas no adapter. Excecoes tecnicas (timeout,
 * 5xx) sobem como exception do RestClient.
 */
public interface PixProvider {

    /**
     * Solicita uma transferencia Pix de saida ao provider. Operacao com efeito externo: recebe
     * {@code idempotencyKey} explicita para deduplicar reenvios. Retorna id externo + status.
     */
    RespostaTransferenciaPix solicitarTransferencia(
            ComandoTransferenciaPix comando, String idempotencyKey, String correlationId);

    /** Consulta o status atual de uma transferencia ja solicitada, pelo id externo do provider. */
    RespostaTransferenciaPix consultarTransferencia(String externalId, String correlationId);

    /**
     * Normaliza um payload bruto de webhook Pix para a linguagem de dominio SEP, calculando o hash
     * do corpo. Mantem o parsing do formato Celcoin isolado no adapter.
     */
    EventoWebhookPixNormalizado normalizarWebhook(String payloadBruto);
}

package com.dynamis.sep_api.pix.application.port.out;

import com.dynamis.sep_api.pix.application.port.out.dto.ComandoCadastrarChavePix;
import com.dynamis.sep_api.pix.application.port.out.dto.ComandoCriarCobrancaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.ComandoTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.EventoWebhookPixNormalizado;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaCadastroChavePix;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaCobrancaPix;
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
     * Cria uma cobranca Pix de recebimento para uma parcela (Sprint 21 Task 21.2). O {@code txid}
     * eh controlado pelo SEP e enviado ao provider para correlacao deterministica do webhook de
     * recebimento. Retorna o id de cobranca do provider + o codigo copia-cola (EMV/QR) quando houver.
     * Falha tecnica sobe como {@code PixProviderException}.
     */
    RespostaCobrancaPix criarCobrancaRecebimento(ComandoCriarCobrancaPix comando, String correlationId);

    /**
     * Normaliza um payload bruto de webhook Pix para a linguagem de dominio SEP, calculando o hash
     * do corpo. Mantem o parsing do formato Celcoin isolado no adapter.
     */
    EventoWebhookPixNormalizado normalizarWebhook(String payloadBruto);

    /**
     * Cadastra uma chave Pix da conta operacional no provider (Sprint 31). Operacao com efeito
     * externo: recebe {@code idempotencyKey} explicita — replay com o mesmo comando retorna a mesma
     * resposta. A chave em claro existe apenas no comando em memoria; a resposta carrega somente o
     * identificador tecnico da chave no provider.
     */
    RespostaCadastroChavePix cadastrarChave(
            ComandoCadastrarChavePix comando, String idempotencyKey, String correlationId);

    /**
     * Remove uma chave Pix no provider pelo identificador tecnico (Sprint 31). Idempotente: remover
     * chave ja removida/inexistente e sucesso. Nunca recebe o valor da chave.
     */
    void removerChave(String providerKeyId, String correlationId);
}

package com.dynamis.sep_api.pix.infrastructure.adapter.fake;

import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.application.port.out.dto.ComandoTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.EventoWebhookPixNormalizado;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.StatusTransferenciaPixProvider;
import com.dynamis.sep_api.pix.infrastructure.adapter.PixWebhookNormalizer;
import com.fasterxml.uuid.Generators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter fake do {@link PixProvider} para dev/test sem credenciais Celcoin (Epic 15 / Sprint 19).
 * Ativado quando {@code app.pix.provider=fake} (default). Substitui o {@code CelcoinPixProvider}.
 *
 * <p>Cenarios deterministicos: {@code solicitarTransferencia} devolve id externo unico em
 * {@code PENDENTE}; {@code consultarTransferencia} devolve {@code CONCLUIDA}. A normalizacao de
 * webhook reusa o {@link PixWebhookNormalizer} (mesmo envelope Celcoin), entao testes de webhook
 * funcionam sem HTTP real.
 */
@Component
@ConditionalOnProperty(name = "app.pix.provider", havingValue = "fake", matchIfMissing = true)
public class FakePixProvider implements PixProvider {

    private static final Logger log = LoggerFactory.getLogger(FakePixProvider.class);

    private final PixWebhookNormalizer webhookNormalizer;

    public FakePixProvider(PixWebhookNormalizer webhookNormalizer) {
        this.webhookNormalizer = webhookNormalizer;
    }

    @Override
    public RespostaTransferenciaPix solicitarTransferencia(
            ComandoTransferenciaPix comando, String idempotencyKey, String correlationId) {
        String externalId = "fake-pix-" + Generators.timeBasedReorderedGenerator().generate();
        log.info("FakePixProvider.solicitarTransferencia valor={} -> {}", comando.valor(), externalId);
        return new RespostaTransferenciaPix(externalId, StatusTransferenciaPixProvider.PENDENTE);
    }

    @Override
    public RespostaTransferenciaPix consultarTransferencia(String externalId, String correlationId) {
        return new RespostaTransferenciaPix(externalId, StatusTransferenciaPixProvider.CONCLUIDA);
    }

    @Override
    public EventoWebhookPixNormalizado normalizarWebhook(String payloadBruto) {
        return webhookNormalizer.normalizar(payloadBruto);
    }
}

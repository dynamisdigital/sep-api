package com.dynamis.sep_api.pix.infrastructure.adapter.fake;

import com.dynamis.sep_api.pix.application.port.out.dto.ComandoCriarCobrancaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.ComandoTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.EventoWebhookPixNormalizado;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaCobrancaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.StatusTransferenciaPixProvider;
import com.dynamis.sep_api.pix.application.port.out.exception.PixProviderException;
import com.dynamis.sep_api.pix.domain.vo.TipoPixWebhookEvent;
import com.dynamis.sep_api.pix.infrastructure.adapter.PixWebhookNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes do {@link FakePixProvider} (dev/test sem credenciais). Cobrem cenarios deterministicos e a
 * normalizacao de webhook delegada ao {@link PixWebhookNormalizer}.
 */
class FakePixProviderTest {

    private FakePixProvider provider;

    @BeforeEach
    void setup() {
        provider = new FakePixProvider(new PixWebhookNormalizer(new ObjectMapper()));
    }

    @Test
    void solicitarTransferenciaDevolveIdExternoPendente() {
        RespostaTransferenciaPix resp = provider.solicitarTransferencia(
                new ComandoTransferenciaPix(new BigDecimal("100.00"), "chave@pix.test", "teste"), "idem-1", "corr-1");

        assertThat(resp.externalId()).startsWith("fake-pix-");
        assertThat(resp.status()).isEqualTo(StatusTransferenciaPixProvider.PENDENTE);
    }

    @Test
    void consultarTransferenciaDevolveConcluida() {
        RespostaTransferenciaPix resp = provider.consultarTransferencia("fake-pix-x", "corr-1");
        assertThat(resp.status()).isEqualTo(StatusTransferenciaPixProvider.CONCLUIDA);
    }

    @Test
    void criarCobrancaRecebimentoEcoaTxidEDevolveProviderRef() {
        RespostaCobrancaPix resp = provider.criarCobrancaRecebimento(
                new ComandoCriarCobrancaPix("txid-abc", new BigDecimal("250.00"), "Recebimento de parcela SEP"),
                "corr-1");

        assertThat(resp.txid()).isEqualTo("txid-abc");
        assertThat(resp.providerReferenciaId()).isEqualTo("fake-cob-txid-abc");
        assertThat(resp.codigoCopiaCola()).contains("txid-abc");
    }

    @Test
    void criarCobrancaArmadaParaFalhar_lancaProviderException() {
        provider.armarFalhaCobranca();

        assertThatThrownBy(() -> provider.criarCobrancaRecebimento(
                        new ComandoCriarCobrancaPix("txid-x", new BigDecimal("10.00"), "desc"), "corr-1"))
                .isInstanceOf(PixProviderException.class);
    }

    @Test
    void normalizarWebhookRecebimentoTraduzTipoEHash() {
        String payload = "{\"event_id\":\"evt-1\",\"event_type\":\"pix.received\","
                + "\"end_to_end_id\":\"E2E-1\",\"amount\":250.00,"
                + "\"txid\":\"txid-1\",\"reference_id\":\"cob-9\"}";

        EventoWebhookPixNormalizado evento = provider.normalizarWebhook(payload);

        assertThat(evento.tipo()).isEqualTo(TipoPixWebhookEvent.RECEBIMENTO_PIX);
        assertThat(evento.eventId()).isEqualTo("evt-1");
        assertThat(evento.endToEndId()).isEqualTo("E2E-1");
        assertThat(evento.valor()).isEqualByComparingTo("250.00");
        assertThat(evento.txid()).isEqualTo("txid-1");
        assertThat(evento.providerReferenciaId()).isEqualTo("cob-9");
        assertThat(evento.payloadHash()).matches("[a-f0-9]{64}");
    }

    @Test
    void normalizarWebhookTipoDesconhecido() {
        String payload = "{\"event_id\":\"evt-2\",\"event_type\":\"foo.bar\"}";
        assertThat(provider.normalizarWebhook(payload).tipo()).isEqualTo(TipoPixWebhookEvent.DESCONHECIDO);
    }

    @Test
    void normalizarWebhookVazioRejeitado() {
        assertThatThrownBy(() -> provider.normalizarWebhook("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizarWebhookInvalidoRejeitado() {
        assertThatThrownBy(() -> provider.normalizarWebhook("nao-json")).isInstanceOf(IllegalArgumentException.class);
    }
}

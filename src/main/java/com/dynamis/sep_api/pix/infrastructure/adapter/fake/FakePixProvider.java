package com.dynamis.sep_api.pix.infrastructure.adapter.fake;

import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.application.port.out.dto.ComandoCriarCobrancaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.ComandoTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.EventoWebhookPixNormalizado;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaCobrancaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.StatusTransferenciaPixProvider;
import com.dynamis.sep_api.pix.application.port.out.exception.PixProviderException;
import com.dynamis.sep_api.pix.infrastructure.adapter.PixWebhookNormalizer;
import com.fasterxml.uuid.Generators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter fake do {@link PixProvider} para dev/test sem credenciais Celcoin (Epic 15 / Sprint 19;
 * estendido na Sprint 20 para desembolso). Ativado quando {@code app.pix.provider=fake} (default).
 *
 * <p>Cenarios deterministicos e configuraveis (para E2E): {@code solicitarTransferencia} devolve id
 * externo unico no status configurado (default {@code PENDENTE}, ou falha tecnica quando armado);
 * {@code consultarTransferencia} devolve o status de consulta configurado (default {@code
 * CONCLUIDA}). A normalizacao de webhook reusa o {@link PixWebhookNormalizer}.
 */
@Component
@ConditionalOnProperty(name = "app.pix.provider", havingValue = "fake", matchIfMissing = true)
public class FakePixProvider implements PixProvider {

    private static final Logger log = LoggerFactory.getLogger(FakePixProvider.class);

    private final PixWebhookNormalizer webhookNormalizer;

    private volatile StatusTransferenciaPixProvider statusSolicitacao = StatusTransferenciaPixProvider.PENDENTE;
    private volatile StatusTransferenciaPixProvider statusConsulta = StatusTransferenciaPixProvider.CONCLUIDA;
    private volatile boolean falharSolicitacao = false;
    private volatile boolean falharCobranca = false;

    public FakePixProvider(PixWebhookNormalizer webhookNormalizer) {
        this.webhookNormalizer = webhookNormalizer;
    }

    /** Configura o status devolvido pela proxima solicitacao (desarma eventual falha). */
    public void configurarStatusSolicitacao(StatusTransferenciaPixProvider status) {
        this.statusSolicitacao = status;
        this.falharSolicitacao = false;
    }

    /** Arma uma falha tecnica na proxima solicitacao. */
    public void armarFalhaSolicitacao() {
        this.falharSolicitacao = true;
    }

    /** Arma uma falha tecnica na proxima criacao de cobranca de recebimento. */
    public void armarFalhaCobranca() {
        this.falharCobranca = true;
    }

    /** Configura o status devolvido pela consulta de status. */
    public void configurarStatusConsulta(StatusTransferenciaPixProvider status) {
        this.statusConsulta = status;
    }

    /** Restaura o comportamento default (PENDENTE na solicitacao, CONCLUIDA na consulta, sem falha). */
    public void reset() {
        this.statusSolicitacao = StatusTransferenciaPixProvider.PENDENTE;
        this.statusConsulta = StatusTransferenciaPixProvider.CONCLUIDA;
        this.falharSolicitacao = false;
        this.falharCobranca = false;
    }

    @Override
    public RespostaTransferenciaPix solicitarTransferencia(
            ComandoTransferenciaPix comando, String idempotencyKey, String correlationId) {
        if (falharSolicitacao) {
            throw new PixProviderException("FakePixProvider: falha tecnica simulada na solicitacao");
        }
        String externalId =
                "fake-pix-" + Generators.timeBasedReorderedGenerator().generate();
        log.info(
                "FakePixProvider.solicitarTransferencia valor={} status={} -> {}",
                comando.valor(),
                statusSolicitacao,
                externalId);
        return new RespostaTransferenciaPix(externalId, statusSolicitacao);
    }

    @Override
    public RespostaTransferenciaPix consultarTransferencia(String externalId, String correlationId) {
        return new RespostaTransferenciaPix(externalId, statusConsulta);
    }

    @Override
    public RespostaCobrancaPix criarCobrancaRecebimento(ComandoCriarCobrancaPix comando, String correlationId) {
        if (falharCobranca) {
            throw new PixProviderException("FakePixProvider: falha tecnica simulada na criacao de cobranca");
        }
        String providerReferenciaId = "fake-cob-" + comando.txid();
        // Copia-cola fake deterministico (correlaciona pelo txid); nao eh um payload EMV valido.
        String codigoCopiaCola = "00020101021126" + comando.txid() + "5204000053039865802BR";
        log.info(
                "FakePixProvider.criarCobrancaRecebimento txid={} valor={} -> {}",
                comando.txid(),
                comando.valor(),
                providerReferenciaId);
        return new RespostaCobrancaPix(comando.txid(), providerReferenciaId, codigoCopiaCola);
    }

    @Override
    public EventoWebhookPixNormalizado normalizarWebhook(String payloadBruto) {
        return webhookNormalizer.normalizar(payloadBruto);
    }
}

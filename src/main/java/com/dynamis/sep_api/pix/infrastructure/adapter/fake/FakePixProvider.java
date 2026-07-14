package com.dynamis.sep_api.pix.infrastructure.adapter.fake;

import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.application.port.out.dto.ComandoCadastrarChavePix;
import com.dynamis.sep_api.pix.application.port.out.dto.ComandoCriarCobrancaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.ComandoTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.EventoWebhookPixNormalizado;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaCadastroChavePix;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaCobrancaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.StatusTransferenciaPixProvider;
import com.dynamis.sep_api.pix.application.port.out.exception.PixProviderException;
import com.dynamis.sep_api.pix.application.service.ChavePixSeguranca;
import com.dynamis.sep_api.pix.infrastructure.adapter.PixWebhookNormalizer;
import com.fasterxml.uuid.Generators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private volatile boolean falharCadastroChave = false;
    private volatile boolean falharRemocaoChave = false;

    /**
     * Cadastro de chave por idempotency key: fingerprint SHA-256 do comando + resposta ja emitida.
     * Guarda-se o fingerprint, nunca o comando — o valor em claro nao fica retido em memoria alem
     * do request (review manual Sprint 31 — P2).
     */
    private final Map<String, CadastroChaveFake> chavesPorIdempotencyKey = new ConcurrentHashMap<>();

    private record CadastroChaveFake(String fingerprint, RespostaCadastroChavePix resposta) {}

    private static String fingerprint(ComandoCadastrarChavePix comando) {
        return ChavePixSeguranca.hashHex(
                comando.tipo() + "|" + comando.valorNormalizado() + "|" + comando.contaTecnicaId());
    }

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

    /** Arma uma falha tecnica no proximo cadastro de chave (Sprint 31). */
    public void armarFalhaCadastroChave() {
        this.falharCadastroChave = true;
    }

    /** Arma uma falha tecnica na proxima remocao de chave (Sprint 31). */
    public void armarFalhaRemocaoChave() {
        this.falharRemocaoChave = true;
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
        this.falharCadastroChave = false;
        this.falharRemocaoChave = false;
        this.chavesPorIdempotencyKey.clear();
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

    /**
     * Cadastro idempotente por idempotency key ({@code computeIfAbsent} atomico): replay com o mesmo
     * comando retorna a mesma resposta; mesma key com comando diferente conflita de forma sanitizada.
     * O log leva apenas tipo e provider id — nunca o valor da chave.
     */
    @Override
    public RespostaCadastroChavePix cadastrarChave(
            ComandoCadastrarChavePix comando, String idempotencyKey, String correlationId) {
        if (falharCadastroChave) {
            throw new PixProviderException("FakePixProvider: falha tecnica simulada no cadastro de chave");
        }
        String fingerprint = fingerprint(comando);
        CadastroChaveFake registro = chavesPorIdempotencyKey.computeIfAbsent(idempotencyKey, key -> {
            String providerKeyId =
                    "fake-key-" + Generators.timeBasedReorderedGenerator().generate();
            return new CadastroChaveFake(fingerprint, new RespostaCadastroChavePix(providerKeyId));
        });
        if (!registro.fingerprint().equals(fingerprint)) {
            throw new PixProviderException(
                    "FakePixProvider: Idempotency-Key de chave reutilizada com comando diferente");
        }
        log.info(
                "FakePixProvider.cadastrarChave tipo={} -> {}",
                comando.tipo(),
                registro.resposta().providerKeyId());
        return registro.resposta();
    }

    /** Remocao idempotente pelo identificador tecnico: repetida ou inexistente e sucesso silencioso. */
    @Override
    public void removerChave(String providerKeyId, String correlationId) {
        if (falharRemocaoChave) {
            throw new PixProviderException("FakePixProvider: falha tecnica simulada na remocao de chave");
        }
        log.info("FakePixProvider.removerChave {}", providerKeyId);
    }
}

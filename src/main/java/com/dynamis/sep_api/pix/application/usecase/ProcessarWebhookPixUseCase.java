package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.application.port.out.dto.EventoWebhookPixNormalizado;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaTransferenciaPix;
import com.dynamis.sep_api.pix.application.service.PixRecebimentoTransacaoService;
import com.dynamis.sep_api.pix.application.service.SincronizadorStatusTransferencia;
import com.dynamis.sep_api.pix.domain.event.PixWebhookFalhouEvent;
import com.dynamis.sep_api.pix.domain.event.PixWebhookProcessadoEvent;
import com.dynamis.sep_api.pix.domain.event.PixWebhookRecebidoEvent;
import com.dynamis.sep_api.pix.domain.model.PixRecebimento;
import com.dynamis.sep_api.pix.domain.model.PixReferenciaRecebimento;
import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.domain.model.PixWebhookEvent;
import com.dynamis.sep_api.pix.domain.vo.StatusPixWebhookEvent;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixRecebimentoRepository;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixReferenciaRecebimentoRepository;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixTransferenciaRepository;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixWebhookEventRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Processamento inicial do webhook Pix/Celcoin (Sprint 19 Task 19.5). Foundation: registra o evento
 * de forma idempotente e, quando for um recebimento, cria o {@link PixRecebimento} inicial — sem
 * conciliar parcela de cobranca nem disparar desembolso (Sprints 20/21).
 *
 * <p>Fluxo:
 *
 * <ol>
 *   <li>Normaliza o payload bruto via {@link PixProvider} (parsing + hash ficam no adapter).
 *   <li>Idempotencia por {@code (provider, event_id)}: evento duplicado retorna sucesso sem
 *       reprocessar.
 *   <li>Persiste {@link PixWebhookEvent} em {@code RECEBIDO} (apenas hash do payload — nunca o JSON
 *       bruto) e roteia por tipo: recebimento cria {@code PixRecebimento} e o correlaciona a uma
 *       {@code PixReferenciaRecebimento} pelo {@code txid} (Sprint 21 — sem referencia vira {@code
 *       NAO_IDENTIFICADO}; a baixa de parcela fica para a Task 21.4); status de transferencia
 *       reconsulta o provider e sincroniza (Sprint 20); tipo desconhecido vira {@code IGNORADO}.
 *   <li>Falha de processamento marca {@code FALHOU} para reprocesso futuro, sem estourar 5xx.
 * </ol>
 */
@Service
public class ProcessarWebhookPixUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarWebhookPixUseCase.class);
    static final String PROVIDER = "celcoin-pix";
    private static final String CODIGO_VALIDACAO = "PIX-400-001";
    private static final int MAX_ERRO = 240;

    private final PixProvider pixProvider;
    private final PixWebhookEventRepository webhookEventRepository;
    private final PixRecebimentoRepository recebimentoRepository;
    private final PixReferenciaRecebimentoRepository referenciaRepository;
    private final PixRecebimentoTransacaoService recebimentoTransacaoService;
    private final PixTransferenciaRepository transferenciaRepository;
    private final SincronizadorStatusTransferencia sincronizador;
    private final ApplicationEventPublisher eventPublisher;

    public ProcessarWebhookPixUseCase(
            PixProvider pixProvider,
            PixWebhookEventRepository webhookEventRepository,
            PixRecebimentoRepository recebimentoRepository,
            PixReferenciaRecebimentoRepository referenciaRepository,
            PixRecebimentoTransacaoService recebimentoTransacaoService,
            PixTransferenciaRepository transferenciaRepository,
            SincronizadorStatusTransferencia sincronizador,
            ApplicationEventPublisher eventPublisher) {
        this.pixProvider = pixProvider;
        this.webhookEventRepository = webhookEventRepository;
        this.recebimentoRepository = recebimentoRepository;
        this.referenciaRepository = referenciaRepository;
        this.recebimentoTransacaoService = recebimentoTransacaoService;
        this.transferenciaRepository = transferenciaRepository;
        this.sincronizador = sincronizador;
        this.eventPublisher = eventPublisher;
    }

    public record Resultado(boolean aceito, boolean duplicado) {}

    @Transactional
    public Resultado executar(String payloadBruto, String correlationId) {
        EventoWebhookPixNormalizado evt = pixProvider.normalizarWebhook(payloadBruto);
        if (evt.eventId() == null || evt.eventId().isBlank()) {
            throw new ValidacaoException(CODIGO_VALIDACAO, "event_id ausente no payload do webhook Pix");
        }

        if (webhookEventRepository.existsByProviderAndEventId(PROVIDER, evt.eventId())) {
            log.info("Webhook Pix duplicado eventId={} — sem reprocessamento", evt.eventId());
            return new Resultado(true, true);
        }

        PixWebhookEvent novo = PixWebhookEvent.receber(PROVIDER, evt.eventId(), evt.tipo(), evt.payloadHash());
        final PixWebhookEvent evento;
        try {
            // Flush imediato (e nao no commit) protege a corrida de MESMO event_id: dois webhooks
            // identicos concorrentes — o segundo bate no unique (provider, event_id) e cai no catch
            // abaixo como duplicado idempotente, sem reprocessar.
            // save() faz merge (id atribuido, sem @Version) — usar a instancia gerenciada retornada
            // para que marcarProcessado/Ignorado/Falhou sejam flushados no commit.
            evento = webhookEventRepository.saveAndFlush(novo);
        } catch (DataIntegrityViolationException ex) {
            // corrida concorrente: outra thread gravou (provider, event_id) primeiro — idempotente
            log.info("Webhook Pix corrida idempotente eventId={}", evt.eventId());
            return new Resultado(true, true);
        }
        eventPublisher.publishEvent(new PixWebhookRecebidoEvent(evt.eventId(), evt.tipo()));

        try {
            switch (evt.tipo()) {
                case RECEBIMENTO_PIX -> {
                    processarRecebimento(evt, correlationId);
                    evento.marcarProcessado();
                }
                case STATUS_TRANSFERENCIA -> {
                    if (reconciliarTransferencia(evt, correlationId)) {
                        evento.marcarProcessado();
                    } else {
                        evento.marcarIgnorado("transferencia desconhecida para o external id do webhook");
                    }
                }
                case DESCONHECIDO -> evento.marcarIgnorado("tipo de evento Pix nao mapeado");
            }
            if (evento.getStatus() == StatusPixWebhookEvent.PROCESSADO) {
                // Coerencia com a persistencia: so anuncia "processado" o que de fato foi processado
                // (evento IGNORADO — ex.: STATUS_TRANSFERENCIA de external id desconhecido — nao publica).
                eventPublisher.publishEvent(new PixWebhookProcessadoEvent(evt.eventId(), evt.tipo()));
            }
        } catch (RuntimeException ex) {
            String motivo = sanitizar(ex);
            evento.marcarFalhou(motivo);
            eventPublisher.publishEvent(new PixWebhookFalhouEvent(evt.eventId(), motivo));
            log.warn(
                    "Webhook Pix processamento falhou eventId={} causa={}",
                    evt.eventId(),
                    ex.getClass().getSimpleName());
        }
        return new Resultado(true, false);
    }

    private void processarRecebimento(EventoWebhookPixNormalizado evt, String correlationId) {
        if (evt.endToEndId() != null
                && recebimentoRepository.findByEndToEndId(evt.endToEndId()).isPresent()) {
            // recebimento ja registrado para este end-to-end id — idempotente
            return;
        }
        PixRecebimento recebimento =
                PixRecebimento.registrar(evt.endToEndId(), evt.valor(), OffsetDateTime.now(), correlationId);

        // Correlacao deterministica (Sprint 21 Task 21.3): localiza a referencia Pix pelo txid (ou,
        // em fallback, pelo id de cobranca do provider). Sem referencia, o recebimento nao baixa
        // parcela automaticamente — fica NAO_IDENTIFICADO para a operacao assistida (backoffice na
        // Task 21.5). Com referencia, vincula referencia/parcela e marca EM_PROCESSAMENTO; a baixa da
        // parcela e o vinculo do recebimento de cobranca/escrow ficam para a Task 21.4.
        Optional<PixReferenciaRecebimento> referencia = localizarReferencia(evt);
        if (referencia.isPresent()) {
            recebimento.vincularReferencia(
                    referencia.get().getId(), referencia.get().getParcelaId());
        } else {
            recebimento.marcarNaoIdentificado();
        }

        try {
            // Insert em tx propria (REQUIRES_NEW): o pre-check acima cobre redelivery sequencial; numa
            // corrida concorrente rara (mesmo end_to_end_id em eventos distintos) a unique parcial de
            // end_to_end_id (V45) falha isolada e reverte so a tx do insert, sem deixar a tx do webhook
            // rollback-only. Tratamos como duplicidade idempotente — nunca credita/baixa duas vezes.
            recebimentoTransacaoService.persistir(recebimento);
        } catch (DataIntegrityViolationException corrida) {
            log.info("Webhook Pix recebimento corrida idempotente endToEndId={}", evt.endToEndId());
        }
    }

    private Optional<PixReferenciaRecebimento> localizarReferencia(EventoWebhookPixNormalizado evt) {
        if (evt.txid() != null && !evt.txid().isBlank()) {
            Optional<PixReferenciaRecebimento> porTxid = referenciaRepository.findByTxid(evt.txid());
            if (porTxid.isPresent()) {
                return porTxid;
            }
        }
        if (evt.providerReferenciaId() != null && !evt.providerReferenciaId().isBlank()) {
            return referenciaRepository.findByProviderReferenciaId(evt.providerReferenciaId());
        }
        return Optional.empty();
    }

    /**
     * Reconcilia uma transferencia de desembolso a partir do webhook {@code STATUS_TRANSFERENCIA}
     * (Sprint 20 Task 20.4). O webhook eh apenas o gatilho: reconsultamos o status autoritativo no
     * provider e sincronizamos idempotentemente. Retorna {@code false} quando o external id eh
     * desconhecido (evento vira {@code IGNORADO}).
     */
    private boolean reconciliarTransferencia(EventoWebhookPixNormalizado evt, String correlationId) {
        String externalId = evt.externalId();
        if (externalId == null || externalId.isBlank()) {
            return false;
        }
        Optional<PixTransferencia> transferencia = transferenciaRepository.findByExternalId(externalId);
        if (transferencia.isEmpty()) {
            return false;
        }
        RespostaTransferenciaPix resposta = pixProvider.consultarTransferencia(externalId, correlationId);
        sincronizador.sincronizar(transferencia.get(), resposta.status());
        transferenciaRepository.save(transferencia.get());
        return true;
    }

    private String sanitizar(RuntimeException ex) {
        String msg = ex.getMessage();
        String base = ex.getClass().getSimpleName() + (msg != null ? ": " + msg : "");
        return base.length() > MAX_ERRO ? base.substring(0, MAX_ERRO) : base;
    }
}

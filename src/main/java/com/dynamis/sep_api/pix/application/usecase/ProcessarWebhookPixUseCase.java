package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.application.port.out.dto.EventoWebhookPixNormalizado;
import com.dynamis.sep_api.pix.domain.model.PixRecebimento;
import com.dynamis.sep_api.pix.domain.model.PixWebhookEvent;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixRecebimentoRepository;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixWebhookEventRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

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
 *       bruto) e roteia por tipo: recebimento cria {@code PixRecebimento}; status de transferencia
 *       e apenas reconhecido (sem acao na foundation); tipo desconhecido vira {@code IGNORADO}.
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

    public ProcessarWebhookPixUseCase(
            PixProvider pixProvider,
            PixWebhookEventRepository webhookEventRepository,
            PixRecebimentoRepository recebimentoRepository) {
        this.pixProvider = pixProvider;
        this.webhookEventRepository = webhookEventRepository;
        this.recebimentoRepository = recebimentoRepository;
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

        try {
            switch (evt.tipo()) {
                case RECEBIMENTO_PIX -> {
                    processarRecebimento(evt, correlationId);
                    evento.marcarProcessado();
                }
                case STATUS_TRANSFERENCIA -> {
                    // Foundation: reconhece o evento; reconciliacao de transferencia fica para Sprints 20/21.
                    evento.marcarProcessado();
                }
                case DESCONHECIDO -> evento.marcarIgnorado("tipo de evento Pix nao mapeado");
            }
        } catch (RuntimeException ex) {
            evento.marcarFalhou(sanitizar(ex));
            log.warn(
                    "Webhook Pix processamento falhou eventId={} causa={}",
                    evt.eventId(),
                    ex.getClass().getSimpleName());
        }
        return new Resultado(true, false);
    }

    private void processarRecebimento(EventoWebhookPixNormalizado evt, String correlationId) {
        if (evt.endToEndId() != null && recebimentoRepository.findByEndToEndId(evt.endToEndId()).isPresent()) {
            // recebimento ja registrado para este end-to-end id — idempotente
            return;
        }
        // Garantia de nao-duplicidade: o unique parcial de end_to_end_id (V45). O pre-check acima
        // cobre redelivery; numa corrida concorrente rara (mesmo end_to_end_id em eventos de id
        // distinto) o insert falha por constraint e a transacao reverte (HTTP 5xx) — o reenvio do
        // provider reconcilia, sem nunca creditar duas vezes. REQUIRES_NEW fica para Sprints 20/21
        // quando houver desembolso real.
        PixRecebimento recebimento =
                PixRecebimento.registrar(evt.endToEndId(), evt.valor(), OffsetDateTime.now(), correlationId);
        recebimentoRepository.save(recebimento);
    }

    private String sanitizar(RuntimeException ex) {
        String msg = ex.getMessage();
        String base = ex.getClass().getSimpleName() + (msg != null ? ": " + msg : "");
        return base.length() > MAX_ERRO ? base.substring(0, MAX_ERRO) : base;
    }
}

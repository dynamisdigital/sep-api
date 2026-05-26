package com.dynamis.sep_api.backoffice.application.usecase;

import com.dynamis.sep_api.backoffice.application.port.out.WebhookReprocessadorPort;
import com.dynamis.sep_api.backoffice.application.port.out.dto.ResultadoReprocesso;
import com.dynamis.sep_api.backoffice.application.service.AntiAbusoReprocessoService;
import com.dynamis.sep_api.backoffice.domain.event.ReprocessoDisparadoEvent;
import com.dynamis.sep_api.backoffice.domain.model.Reprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.StatusReprocesso;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ReprocessoRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Re-dispara processamento de evento da Outbox (Sprint 14 Task 14.4).
 *
 * <p>Ordem (fix code review Task 14.4): anti-abuso -&gt; adapter -&gt; criar Reprocesso ja com
 * status final -&gt; salvar 1 vez -&gt; publicar event. Tudo na mesma tx outer; se save falhar,
 * rollback unificado tambem desfaz a marcacao do {@code WebhookEventLog} (adapter agora usa
 * {@code @Transactional} padrao). Step-up MFA na borda REST (Task 14.7).
 */
@Service
public class ReprocessarWebhookUseCase {

    private final ReprocessoRepository reprocessoRepository;
    private final WebhookReprocessadorPort webhookReprocessador;
    private final AntiAbusoReprocessoService antiAbuso;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ReprocessarWebhookUseCase(
            ReprocessoRepository reprocessoRepository,
            WebhookReprocessadorPort webhookReprocessador,
            AntiAbusoReprocessoService antiAbuso,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.reprocessoRepository = reprocessoRepository;
        this.webhookReprocessador = webhookReprocessador;
        this.antiAbuso = antiAbuso;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Reprocesso executar(UUID webhookEventId, UUID disparadoPor, UUID itemId) {
        antiAbuso.validarLimite(webhookEventId.toString());

        ResultadoReprocesso resultado = webhookReprocessador.reprocessar(webhookEventId);

        Reprocesso reprocesso = Reprocesso.paraWebhook(itemId, webhookEventId, OffsetDateTime.now(clock), disparadoPor);
        aplicarResultado(reprocesso, resultado);
        Reprocesso salvo = reprocessoRepository.save(reprocesso);

        eventPublisher.publishEvent(new ReprocessoDisparadoEvent(
                salvo.getId(), salvo.getTipo(), salvo.getIdentificadorExterno(), disparadoPor));
        return salvo;
    }

    private static void aplicarResultado(Reprocesso reprocesso, ResultadoReprocesso resultado) {
        if (resultado.status() == StatusReprocesso.SUCESSO) {
            reprocesso.concluirComSucesso(resultado.mensagemTecnica());
        } else {
            reprocesso.concluirComFalha(resultado.mensagemTecnica());
        }
    }
}

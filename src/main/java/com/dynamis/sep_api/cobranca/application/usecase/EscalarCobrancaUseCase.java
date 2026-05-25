package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.dto.EscalarCobrancaCommand;
import com.dynamis.sep_api.cobranca.application.dto.EscalonamentoResult;
import com.dynamis.sep_api.cobranca.application.port.out.NotificationProvider;
import com.dynamis.sep_api.cobranca.application.port.out.dto.Notificacao;
import com.dynamis.sep_api.cobranca.application.port.out.dto.ResultadoNotificacao;
import com.dynamis.sep_api.cobranca.application.service.workflow.WorkflowCobrancaResolver;
import com.dynamis.sep_api.cobranca.application.service.workflow.WorkflowCobrancaResolver.EtapaWorkflow;
import com.dynamis.sep_api.cobranca.application.service.workflow.WorkflowCobrancaResolver.NotificacaoEtapa;
import com.dynamis.sep_api.cobranca.domain.event.EtapaCobrancaAplicadaEvent;
import com.dynamis.sep_api.cobranca.domain.model.EventoCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;
import com.dynamis.sep_api.cobranca.domain.vo.StatusEventoCobranca;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.EventoCobrancaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Aplica a etapa do workflow de cobranca correspondente a {@code diasAtraso} (Sprint 13 Task 13.4).
 *
 * <p>Resolve etapa exata via {@link WorkflowCobrancaResolver}, itera as notificacoes configuradas,
 * checa idempotencia em {@code EventoCobrancaRepository} pra evitar reemissao no mesmo dia/canal/
 * template, despacha pra {@link NotificationProvider} compativel com o canal e persiste {@link
 * EventoCobranca} para cada tentativa (sucesso ou falha).
 *
 * <p>Use case nao decide transicoes de status — apenas devolve {@link EscalonamentoResult} com as
 * flags da etapa (contato manual, backoffice, marcar inadimplente). Quem aciona ({@code
 * ParcelaAtrasouListener}/{@code EscaladorCobrancaJob}/{@code MarcarParcelaInadimplenteJob} Task
 * 13.5) interpreta as flags.
 */
@Service
public class EscalarCobrancaUseCase {

    private static final Logger log = LoggerFactory.getLogger(EscalarCobrancaUseCase.class);

    private final WorkflowCobrancaResolver resolver;
    private final List<NotificationProvider> providers;
    private final EventoCobrancaRepository eventoRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public EscalarCobrancaUseCase(
            WorkflowCobrancaResolver resolver,
            List<NotificationProvider> providers,
            EventoCobrancaRepository eventoRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.resolver = resolver;
        this.providers = providers;
        this.eventoRepository = eventoRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public EscalonamentoResult escalar(EscalarCobrancaCommand command) {
        Optional<EtapaWorkflow> etapaOpt = resolver.etapaParaDia(command.diasAtraso());
        if (etapaOpt.isEmpty()) {
            return EscalonamentoResult.semEtapa();
        }
        EtapaWorkflow etapa = etapaOpt.get();
        int eventosCriados = 0;
        for (NotificacaoEtapa notif : etapa.notificacoes()) {
            if (jaEnviado(command.parcelaId(), command.diasAtraso(), notif)) {
                continue;
            }
            eventosCriados += disparar(command, notif);
        }
        // Fix review manual Task 13.4: callers anteriores descartavam EscalonamentoResult, deixando
        // flagContatoManual/escalonarBackoffice/marcarInadimplente sem sinal observavel. Publicar
        // evento Spring permite que listeners dedicados (Task 13.5 MarcarParcelaInadimplenteJob /
        // Sprint 14 backoffice) reajam de forma desacoplada e auditavel.
        eventPublisher.publishEvent(new EtapaCobrancaAplicadaEvent(
                command.parcelaId(),
                command.diasAtraso(),
                etapa.flagContatoManual(),
                etapa.escalonarBackoffice(),
                etapa.marcarInadimplente(),
                eventosCriados));
        return new EscalonamentoResult(
                true,
                etapa.flagContatoManual(),
                etapa.escalonarBackoffice(),
                etapa.marcarInadimplente(),
                eventosCriados);
    }

    private boolean jaEnviado(java.util.UUID parcelaId, int diasAtraso, NotificacaoEtapa notif) {
        return eventoRepository.existsByParcelaIdAndDiasAtrasoAndCanalAndTemplate(
                parcelaId, diasAtraso, notif.canal(), notif.template());
    }

    private int disparar(EscalarCobrancaCommand command, NotificacaoEtapa notif) {
        String destinatario = destinatario(command, notif.canal());
        OffsetDateTime agora = OffsetDateTime.now(clock);
        if (destinatario == null || destinatario.isBlank()) {
            persistirFalha(command, notif, agora, "destinatario indisponivel");
            log.info(
                    "Pulou {} (parcela={}, dias={}): destinatario indisponivel",
                    notif.canal(),
                    command.parcelaId(),
                    command.diasAtraso());
            return 1;
        }
        Optional<NotificationProvider> providerOpt =
                providers.stream().filter(p -> p.suporta(notif.canal())).findFirst();
        if (providerOpt.isEmpty()) {
            // Hotfix Task 13.4 code review: provider ausente NAO pode propagar IllegalStateException
            // — a @Transactional do escalar() faria rollback e perderia eventos das iteracoes
            // anteriores (ex.: email enviado OK + SMS sem provider). Persistir como FALHA mantem
            // trilha auditavel sem repetir o canal ja entregue no proximo dia.
            persistirFalha(command, notif, agora, "provider ausente para " + notif.canal());
            log.warn(
                    "Nenhum NotificationProvider suporta canal {} (parcela={}, dias={})",
                    notif.canal(),
                    command.parcelaId(),
                    command.diasAtraso());
            return 1;
        }
        ResultadoNotificacao resultado;
        try {
            resultado = providerOpt
                    .get()
                    .enviar(new Notificacao(
                            notif.canal(),
                            destinatario,
                            notif.template(),
                            command.variaveis(),
                            command.correlationId()));
        } catch (RuntimeException e) {
            // Fix review manual Task 13.4: provider.enviar() pode lancar (timeout, HTTP,
            // Resilience4j call-not-permitted, render erro). Sem try/catch a exceção propaga
            // pelo escalar() @Transactional e rolla rollback — eventos das iteracoes anteriores
            // sao perdidos e o envio ja entregue eh repetido no proximo ciclo (idempotencia
            // do unique parcial ainda protege, mas eventos somem da trilha).
            log.warn(
                    "Provider {} lancou excecao (parcela={}, dias={}, canal={})",
                    providerOpt.get().nome(),
                    command.parcelaId(),
                    command.diasAtraso(),
                    notif.canal(),
                    e);
            persistirFalha(
                    command, notif, agora, "provider lancou: " + e.getClass().getSimpleName());
            return 1;
        }
        eventoRepository.save(EventoCobranca.notificacaoAutomatica(
                command.parcelaId(),
                notif.canal(),
                notif.template(),
                command.diasAtraso(),
                resultado.status(),
                resultado.mensagemTecnica(),
                agora));
        return 1;
    }

    private void persistirFalha(
            EscalarCobrancaCommand command, NotificacaoEtapa notif, OffsetDateTime agora, String motivo) {
        eventoRepository.save(EventoCobranca.notificacaoAutomatica(
                command.parcelaId(),
                notif.canal(),
                notif.template(),
                command.diasAtraso(),
                StatusEventoCobranca.FALHA,
                motivo,
                agora));
    }

    private static String destinatario(EscalarCobrancaCommand command, CanalNotificacao canal) {
        return switch (canal) {
            case EMAIL -> command.emailTomador();
            case SMS -> command.telefoneTomador();
        };
    }
}

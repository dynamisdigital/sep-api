package com.dynamis.sep_api.cobranca.application.job;

import com.dynamis.sep_api.cobranca.domain.event.ParcelaAtrasouEvent;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Job diario (cron {@code app.cobranca.job-atraso-cron}, default 02:00 America/Sao_Paulo) que
 * marca como {@link StatusParcela#ATRASADA} as parcelas {@code PENDENTE} com vencimento anterior
 * ao dia corrente (Sprint 12 Task 12.5).
 *
 * <p>Publica {@link ParcelaAtrasouEvent} uma unica vez por transicao. Reexecucao do job nao
 * republica eventos pra parcelas que ja estao {@code ATRASADA} (apenas {@code PENDENTE} entra na
 * consulta).
 *
 * <p>{@link StatusParcela#INADIMPLENTE} eh reservado pra Sprint 13 — este job nao alcanca.
 *
 * <p><b>Concorrencia em multi-instance:</b> Sprint 12 opera em single-instance ({@code dev-local}).
 * Em deploy clustered (Epic 15 AWS), duas instancias com cron sincronizado poderiam ler o mesmo
 * conjunto {@code PENDENTE} e publicar eventos duplicados. Mitigacao planejada: ShedLock ou
 * advisory lock PostgreSQL coordenando uma execucao por janela. Nao introduzido nesta sprint pra
 * manter escopo single-instance.
 */
@Component
public class MarcarParcelaAtrasadaJob {

    private static final Logger log = LoggerFactory.getLogger(MarcarParcelaAtrasadaJob.class);

    private final ParcelaCobrancaRepository parcelaRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public MarcarParcelaAtrasadaJob(
            ParcelaCobrancaRepository parcelaRepository, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.parcelaRepository = parcelaRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.cobranca.job-atraso-cron:0 0 2 * * *}", zone = "America/Sao_Paulo")
    @Transactional
    public int executar() {
        LocalDate hoje = LocalDate.now(clock);
        List<ParcelaCobranca> vencidas =
                parcelaRepository.findByStatusAndDataVencimentoBefore(StatusParcela.PENDENTE, hoje);
        for (ParcelaCobranca parcela : vencidas) {
            parcela.marcarAtrasada();
            eventPublisher.publishEvent(new ParcelaAtrasouEvent(
                    parcela.getId(),
                    parcela.getAgenda().getId(),
                    parcela.getAgenda().getContratoId(),
                    parcela.getNumero(),
                    parcela.getDataVencimento()));
        }
        if (!vencidas.isEmpty()) {
            log.info("MarcarParcelaAtrasadaJob marcou {} parcela(s) como ATRASADA em {}", vencidas.size(), hoje);
        }
        return vencidas.size();
    }
}

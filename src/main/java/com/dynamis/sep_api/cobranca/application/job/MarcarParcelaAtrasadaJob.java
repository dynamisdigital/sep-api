package com.dynamis.sep_api.cobranca.application.job;

import com.dynamis.sep_api.cobranca.domain.event.ParcelaAtrasouEvent;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.shared.infrastructure.job.PostgresAdvisoryJobLock;
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
 * <p><b>Concorrencia em multi-instance (Sprint 15 — 15F-003):</b> usa {@link
 * PostgresAdvisoryJobLock} com chave estavel {@link #LOCK_KEY}. Em deploy clustered, apenas a
 * instancia que adquirir o lock advisory executa; demais retornam 0 imediatamente.
 */
@Component
public class MarcarParcelaAtrasadaJob {

    /** Chave estavel do advisory lock — derivada de hash do nome da classe. */
    public static final long LOCK_KEY = 0x4D41524350415441L; // "MARCPATA"

    private static final Logger log = LoggerFactory.getLogger(MarcarParcelaAtrasadaJob.class);

    private final ParcelaCobrancaRepository parcelaRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PostgresAdvisoryJobLock jobLock;
    private final Clock clock;

    public MarcarParcelaAtrasadaJob(
            ParcelaCobrancaRepository parcelaRepository,
            ApplicationEventPublisher eventPublisher,
            PostgresAdvisoryJobLock jobLock,
            Clock clock) {
        this.parcelaRepository = parcelaRepository;
        this.eventPublisher = eventPublisher;
        this.jobLock = jobLock;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.cobranca.job-atraso-cron:0 0 2 * * *}", zone = "America/Sao_Paulo")
    @Transactional
    public int executar() {
        return jobLock.runIfAcquired(LOCK_KEY, this::marcarParcelasVencidas);
    }

    private int marcarParcelasVencidas() {
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

package com.dynamis.sep_api.cobranca.application.job;

import com.dynamis.sep_api.cobranca.domain.event.RenegociacaoExpiradaEvent;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.model.Renegociacao;
import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.RenegociacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Job diario que expira renegociacoes PROPOSTA sem decisao apos a janela de 7 dias (Sprint 13
 * Task 13.6).
 *
 * <p>Pipeline por renegociacao expirada:
 *
 * <ol>
 *   <li>Lock pessimista (via findById dentro de tx) + guard {@code status == PROPOSTA}.
 *   <li>Marca renegociacao {@code EXPIRADA}.
 *   <li>Parcela volta ao {@code statusParcelaAnterior} ({@code ATRASADA} ou {@code INADIMPLENTE}).
 *   <li>Publica {@link RenegociacaoRecusadaEvent} (mesmo evento que recusa explicita — payload
 *       distingue via {@code statusParcelaRevertido}; listeners de audit/notif diferenciam pelo
 *       {@code Renegociacao.status} se precisarem).
 * </ol>
 *
 * <p>Cron 04:00 America/Sao_Paulo — depois do escalador (03:00) pra nao sobrepor execucoes.
 */
@Component
@ConditionalOnProperty(name = "app.cobranca.scheduling-habilitado", havingValue = "true", matchIfMissing = true)
public class ExpirarRenegociacaoJob {

    private static final Logger log = LoggerFactory.getLogger(ExpirarRenegociacaoJob.class);

    private final RenegociacaoRepository renegociacaoRepository;
    private final ParcelaCobrancaRepository parcelaRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate txTemplate;
    private final Clock clock;

    public ExpirarRenegociacaoJob(
            RenegociacaoRepository renegociacaoRepository,
            ParcelaCobrancaRepository parcelaRepository,
            ApplicationEventPublisher eventPublisher,
            TransactionTemplate txTemplate,
            Clock clock) {
        this.renegociacaoRepository = renegociacaoRepository;
        this.parcelaRepository = parcelaRepository;
        this.eventPublisher = eventPublisher;
        this.txTemplate = txTemplate;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.cobranca.renegociacao-expirar-cron:0 0 4 * * *}", zone = "America/Sao_Paulo")
    public void expirarDiariamente() {
        executar();
    }

    /** Publico pra testes — controla {@code Clock} e invoca sem agendamento. */
    public int executar() {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        // Fix review manual Task 13.6: inclui o instante exato (LessThanEqual) pra casar com
        // Renegociacao.expirouEm — sem isso, proposta com dataExpiracao == agora ficaria pro
        // proximo ciclo.
        List<Renegociacao> elegiveis =
                renegociacaoRepository.findByStatusAndDataExpiracaoLessThanEqual(StatusRenegociacao.PROPOSTA, agora);
        int processadas = 0;
        for (Renegociacao renegociacao : elegiveis) {
            try {
                Boolean expirou = txTemplate.execute(status -> expirar(renegociacao.getId(), agora));
                if (Boolean.TRUE.equals(expirou)) {
                    processadas++;
                }
            } catch (RuntimeException e) {
                log.atError()
                        .addKeyValue("event", "job_failed")
                        .addKeyValue("job", "expirar_renegociacao")
                        .addKeyValue("entityId", renegociacao.getId())
                        .setCause(e)
                        .log("Falha ao expirar renegociacao");
            }
        }
        if (processadas > 0) {
            log.info("ExpirarRenegociacaoJob: {} renegociacoes expiradas em {}", processadas, agora);
        }
        return processadas;
    }

    private Boolean expirar(UUID renegociacaoId, OffsetDateTime agora) {
        // Hotfix code review Task 13.6: lock pessimista serializa expirar vs aceitar/recusar.
        Renegociacao atual =
                renegociacaoRepository.findByIdForUpdate(renegociacaoId).orElse(null);
        if (atual == null || atual.getStatus() != StatusRenegociacao.PROPOSTA) {
            // Outra instancia ja decidiu/expirou — idempotente.
            return false;
        }
        ParcelaCobranca parcela = parcelaRepository
                .findByIdForUpdate(atual.getParcelaOriginalId())
                .orElse(null);
        if (parcela == null) {
            log.warn("ExpirarRenegociacaoJob: parcela {} sumiu antes do lock", atual.getParcelaOriginalId());
            return false;
        }
        parcela.reverterDeNegociacao(atual.getStatusParcelaAnterior());
        parcelaRepository.save(parcela);
        atual.expirar(agora);
        renegociacaoRepository.save(atual);
        // Fix review manual Task 13.6: RenegociacaoExpiradaEvent eh distinto de Recusada —
        // expiracao eh inacao, recusa eh ato do tomador. Audit/backoffice (Sprint 14) precisa
        // diferenciar pra metrica de engajamento.
        eventPublisher.publishEvent(new RenegociacaoExpiradaEvent(
                atual.getId(), parcela.getId(), atual.getTomadorId(), atual.getStatusParcelaAnterior()));
        return true;
    }
}

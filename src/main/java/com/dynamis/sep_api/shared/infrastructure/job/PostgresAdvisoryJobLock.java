package com.dynamis.sep_api.shared.infrastructure.job;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.function.IntSupplier;

/**
 * Coordenador de execucao unica entre instancias usando {@code pg_try_advisory_lock} do
 * PostgreSQL (Sprint 15 — 15F-003).
 *
 * <p>Em deploy clustered (Epic 15 AWS), dois nodes com cron sincronizado podem ler o mesmo
 * conjunto de registros e duplicar efeitos colaterais (eventos, notificacoes, audit). O lock
 * advisory atua como mutex distribuido por chave: apenas o primeiro caller que conseguir o lock
 * executa o bloco; demais retornam imediatamente com {@code 0}.
 *
 * <p>Lock e session-scoped: liberado automaticamente ao fim da sessao JDBC ou via
 * {@code pg_advisory_unlock}. Aqui usamos try-finally pra release explicito apos a execucao.
 *
 * <p>Uso tipico em job Spring:
 * <pre>
 *   @Scheduled(cron = "...")
 *   @Transactional
 *   public int executar() {
 *       return jobLock.runIfAcquired(LOCK_KEY, this::doExecutar);
 *   }
 * </pre>
 */
@Component
public class PostgresAdvisoryJobLock {

    @PersistenceContext
    private EntityManager em;

    /**
     * Tenta adquirir o lock para {@code lockKey}. Se conseguir, executa {@code work} e libera o
     * lock no final. Se outra instancia ja detem o lock, retorna 0 sem executar.
     *
     * @param lockKey chave estavel por job (use constante como {@code MarcarParcelaAtrasadaJob.LOCK_KEY})
     * @param work bloco a executar quando o lock for adquirido
     * @return resultado de {@code work}, ou 0 se o lock nao foi adquirido
     */
    public int runIfAcquired(long lockKey, IntSupplier work) {
        Object acquired = em.createNativeQuery("SELECT pg_try_advisory_lock(:k)")
                .setParameter("k", lockKey)
                .getSingleResult();
        if (!Boolean.TRUE.equals(acquired)) {
            return 0;
        }
        try {
            return work.getAsInt();
        } finally {
            em.createNativeQuery("SELECT pg_advisory_unlock(:k)")
                    .setParameter("k", lockKey)
                    .getSingleResult();
        }
    }
}

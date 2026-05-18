package com.dynamis.sep_api.credito.application.listener;

import com.dynamis.sep_api.credito.application.usecase.AvaliarPropostaUseCase;
import com.dynamis.sep_api.credito.domain.event.PropostaCriadaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Dispara avaliacao automatica do motor apos {@link PropostaCriadaEvent} ser comitado. Usa
 * {@link TransactionPhase#AFTER_COMMIT} pra garantir que a avaliacao so roda quando a proposta
 * existe efetivamente no banco; combina com {@code REQUIRES_NEW} no {@link AvaliarPropostaUseCase}
 * pra abrir transacao propria (sem isso, {@code @Transactional} default {@code REQUIRED} se
 * juntaria a tx fantasma do AFTER_COMMIT e perderia saves silenciosamente — ver Sprint 7
 * {@code PldOrchestrationListener}).
 */
@Component
public class PropostaAvaliacaoListener {

    private static final Logger log = LoggerFactory.getLogger(PropostaAvaliacaoListener.class);

    private final AvaliarPropostaUseCase avaliarPropostaUseCase;

    public PropostaAvaliacaoListener(AvaliarPropostaUseCase avaliarPropostaUseCase) {
        this.avaliarPropostaUseCase = avaliarPropostaUseCase;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPropostaCriada(PropostaCriadaEvent event) {
        try {
            avaliarPropostaUseCase.executar(event.propostaId());
        } catch (RuntimeException ex) {
            log.error("Falha ao avaliar proposta {}: {}", event.propostaId(), ex.getMessage(), ex);
        }
    }
}

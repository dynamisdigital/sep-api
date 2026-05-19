package com.dynamis.sep_api.credito.application.listener;

import com.dynamis.sep_api.credito.application.usecase.ReavaliarPropostaComOpenFinanceUseCase;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceDadosRecebidosEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Apos {@link OpenFinanceDadosRecebidosEvent} ser comitado (snapshot persistido em
 * {@code movimentacao_open_finance}), dispara {@link ReavaliarPropostaComOpenFinanceUseCase}
 * em transacao isolada (Sprint 9 Task 9.4).
 *
 * <p>{@code AFTER_COMMIT}: falha na reavaliacao nao desfaz a gravacao do snapshot — score continua
 * o anterior; auditoria registra apenas a presenca dos dados (sem comparativo). Padrao identico
 * ao {@code PropostaAvaliacaoListener} (Sprint 8).
 */
@Component
public class OpenFinanceDadosRecebidosListener {

    private static final Logger log = LoggerFactory.getLogger(OpenFinanceDadosRecebidosListener.class);

    private final ReavaliarPropostaComOpenFinanceUseCase reavaliarUseCase;

    public OpenFinanceDadosRecebidosListener(ReavaliarPropostaComOpenFinanceUseCase reavaliarUseCase) {
        this.reavaliarUseCase = reavaliarUseCase;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDadosRecebidos(OpenFinanceDadosRecebidosEvent event) {
        try {
            reavaliarUseCase.executar(event.propostaId(), event.consentimentoId());
        } catch (RuntimeException ex) {
            log.error(
                    "Falha ao reavaliar proposta com Open Finance propostaId={}: {}",
                    event.propostaId(),
                    ex.getMessage(),
                    ex);
        }
    }
}

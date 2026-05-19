package com.dynamis.sep_api.credito.application.listener;

import com.dynamis.sep_api.credito.application.usecase.ConsultarMovimentacaoOpenFinanceUseCase;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceAutorizadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Apos {@link OpenFinanceAutorizadoEvent} ser comitado (consentimento marcado como AUTORIZADO no
 * banco), dispara {@link ConsultarMovimentacaoOpenFinanceUseCase} em transacao isolada (Sprint 9
 * Task 9.3 + 9.4).
 *
 * <p>{@code AFTER_COMMIT}: falha na consulta nao desfaz a autorizacao do consentimento — provider
 * Celcoin/Finansystech ja registrou autorizacao do tomador. Idempotencia da consulta cobre retry
 * manual depois (callback Celcoin pode repetir).
 */
@Component
public class OpenFinanceAutorizadoListener {

    private static final Logger log = LoggerFactory.getLogger(OpenFinanceAutorizadoListener.class);

    private final ConsultarMovimentacaoOpenFinanceUseCase consultarUseCase;

    public OpenFinanceAutorizadoListener(ConsultarMovimentacaoOpenFinanceUseCase consultarUseCase) {
        this.consultarUseCase = consultarUseCase;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAutorizado(OpenFinanceAutorizadoEvent event) {
        try {
            consultarUseCase.executar(event.consentimentoId());
        } catch (RuntimeException ex) {
            log.error(
                    "Falha ao consultar movimentacao Open Finance consentimentoId={}: {}",
                    event.consentimentoId(),
                    ex.getMessage(),
                    ex);
        }
    }
}

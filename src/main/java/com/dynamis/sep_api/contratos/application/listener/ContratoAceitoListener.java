package com.dynamis.sep_api.contratos.application.listener;

import com.dynamis.sep_api.contratos.application.usecase.EnviarParaAssinaturaUseCase;
import com.dynamis.sep_api.contratos.domain.event.ContratoAceitoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Liga {@link ContratoAceitoEvent} ao envio automatico para assinatura digital (Sprint 11 Task
 * 11.5).
 *
 * <p>{@link TransactionalEventListener} com {@link TransactionPhase#AFTER_COMMIT} garante que o
 * envio so dispara se o aceite foi commitado. {@link Propagation#REQUIRES_NEW} abre transacao
 * propria — falha de envio nao reverte o aceite ja persistido. Padrao identico ao {@code
 * ContratoAuditListener} (Sprint 10) e {@code PldOrchestrationListener} (Sprint 7).
 *
 * <p>Falha de envio (provider indisponivel, 5xx) e tratada como pendencia operacional: log warn
 * + envelope nao criado. Reprocessamento via endpoint manual {@code POST /contratos/{id}/assinar}
 * (Task 11.7).
 */
@Component
@ConditionalOnProperty(name = "app.assinatura.auto-envio-pos-aceite", havingValue = "true", matchIfMissing = true)
public class ContratoAceitoListener {

    private static final Logger log = LoggerFactory.getLogger(ContratoAceitoListener.class);

    private final EnviarParaAssinaturaUseCase enviarUseCase;

    public ContratoAceitoListener(EnviarParaAssinaturaUseCase enviarUseCase) {
        this.enviarUseCase = enviarUseCase;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAceitar(ContratoAceitoEvent event) {
        try {
            enviarUseCase.executar(event.contratoId(), "aceite:" + event.contratoId());
        } catch (RuntimeException ex) {
            // Aceite ja commitado; falha aqui nao desfaz aceite. Operador reprocessa via
            // endpoint manual POST /contratos/{id}/assinar (Task 11.7).
            log.warn(
                    "Falha ao enviar contrato {} para assinatura apos aceite — operador deve reprocessar: {}",
                    event.contratoId(),
                    ex.getMessage());
        }
    }
}

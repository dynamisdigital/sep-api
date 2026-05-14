package com.dynamis.sep_api.onboarding.application.listener;

import com.dynamis.sep_api.onboarding.application.usecase.IniciarPldEmpresaUseCase;
import com.dynamis.sep_api.onboarding.application.usecase.IniciarPldPessoaUseCase;
import com.dynamis.sep_api.onboarding.domain.event.KybFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.OnboardingFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Orquestra o disparo do PLD apos KYC/KYB serem aprovados.
 *
 * <ul>
 *   <li>{@link OnboardingFinalizadoEvent} (Sprint 6 — PF) → {@code IniciarPldPessoaUseCase} quando
 *       statusFinal e {@code APROVADO}.
 *   <li>{@link KybFinalizadoEvent} (Sprint 7 — PJ) → {@code IniciarPldEmpresaUseCase} quando
 *       statusFinal e {@code APROVADO}.
 * </ul>
 *
 * <p>Listeners executam {@code AFTER_COMMIT} pra evitar disparar PLD sobre transacao revertida.
 * Falhas no PLD nao reabrem a transacao de KYC/KYB; ficam capturadas no log + audit.
 */
@Component
public class PldOrchestrationListener {

    private static final Logger log = LoggerFactory.getLogger(PldOrchestrationListener.class);

    private final IniciarPldPessoaUseCase pldPessoa;
    private final IniciarPldEmpresaUseCase pldEmpresa;

    public PldOrchestrationListener(IniciarPldPessoaUseCase pldPessoa, IniciarPldEmpresaUseCase pldEmpresa) {
        this.pldPessoa = pldPessoa;
        this.pldEmpresa = pldEmpresa;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onKycFinalizado(OnboardingFinalizadoEvent event) {
        if (event.statusFinal() != StatusOnboarding.APROVADO) {
            return;
        }
        try {
            pldPessoa.executar(event.solicitacaoId(), "kyc-followup-" + event.solicitacaoId());
        } catch (RuntimeException ex) {
            log.error("Falha PLD followup PF solicitacaoId={}: {}", event.solicitacaoId(), ex.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onKybFinalizado(KybFinalizadoEvent event) {
        if (event.statusFinal() != StatusOnboarding.APROVADO) {
            return;
        }
        try {
            pldEmpresa.executar(event.solicitacaoId(), "kyb-followup-" + event.solicitacaoId());
        } catch (RuntimeException ex) {
            log.error("Falha PLD followup PJ solicitacaoId={}: {}", event.solicitacaoId(), ex.getMessage());
        }
    }
}

package com.dynamis.sep_api.contratos.application.listener;

import com.dynamis.sep_api.contratos.application.usecase.GerarContratoUseCase;
import com.dynamis.sep_api.contratos.application.usecase.command.GerarContratoCommand;
import com.dynamis.sep_api.credito.domain.event.PropostaAprovadaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Dispara {@link GerarContratoUseCase} quando proposta de credito e aprovada (Sprint 10 Task
 * 10.3).
 *
 * <p>{@link TransactionalEventListener} com {@link TransactionPhase#AFTER_COMMIT} garante que
 * eventos de transacoes revertidas nao iniciem formalizacao. O handler usa
 * {@link Propagation#REQUIRES_NEW} porque AFTER_COMMIT roda fora da transacao original — sem novo
 * escopo transacional explicito, persist do {@code Contrato} se juntaria a uma transacao ja
 * commitada e os saves seriam perdidos silenciosamente (mesma licao da Sprint 7
 * {@code PldOrchestrationListener} e Sprint 9 {@code OpenFinanceAutorizadoListener}).
 *
 * <p>Falhas sao logadas mas nao propagadas — a proposta ja foi aprovada e nao deve ser revertida
 * por falha de formalizacao; uma pendencia operacional sera criada em sprint futura (backoffice).
 */
@Component
public class PropostaAprovadaListener {

    private static final Logger log = LoggerFactory.getLogger(PropostaAprovadaListener.class);

    private final GerarContratoUseCase gerarContratoUseCase;

    public PropostaAprovadaListener(GerarContratoUseCase gerarContratoUseCase) {
        this.gerarContratoUseCase = gerarContratoUseCase;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAprovar(PropostaAprovadaEvent event) {
        try {
            gerarContratoUseCase.executar(new GerarContratoCommand(event.propostaId()));
        } catch (RuntimeException e) {
            log.error(
                    "Falha ao gerar contrato apos aprovacao da proposta {}: {}", event.propostaId(), e.getMessage(), e);
        }
    }
}

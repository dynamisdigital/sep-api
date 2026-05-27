package com.dynamis.sep_api.backoffice.application.listener;

import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService.CriarItemCommand;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.onboarding.domain.event.OnboardingFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cria item na fila operacional quando onboarding finaliza com {@code REPROVADO}, {@code PENDENCIA}
 * ou {@code REPROVADO_PLD} (Sprint 14 Task 14.2).
 *
 * <ul>
 *   <li>{@code REPROVADO} / {@code REPROVADO_PLD} -> {@code ONBOARDING_ERRO} prioridade ALTA.
 *   <li>{@code PENDENCIA} -> {@code ONBOARDING_PENDENTE} prioridade MEDIA.
 * </ul>
 *
 * <p>Idempotencia + isolamento transacional sao garantidos pelo
 * {@link CriarItemFilaOperacionalService}; falha aqui nao quebra o fluxo de onboarding.
 */
@Component
public class OnboardingFinalizadoListener {

    private static final Logger LOG = LoggerFactory.getLogger(OnboardingFinalizadoListener.class);

    private final CriarItemFilaOperacionalService criarItem;

    public OnboardingFinalizadoListener(CriarItemFilaOperacionalService criarItem) {
        this.criarItem = criarItem;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoFinalizar(OnboardingFinalizadoEvent event) {
        try {
            tratar(event);
        } catch (RuntimeException ex) {
            LOG.error("falha ao criar item fila pra onboarding {}; ignorada", event.solicitacaoId(), ex);
        }
    }

    private void tratar(OnboardingFinalizadoEvent event) {
        StatusOnboarding status = event.statusFinal();
        TipoItemFila tipo;
        PrioridadeItem prioridade;
        String titulo;

        switch (status) {
            case REPROVADO, REPROVADO_PLD -> {
                tipo = TipoItemFila.ONBOARDING_ERRO;
                prioridade = PrioridadeItem.ALTA;
                titulo = "Onboarding " + status + " requer revisao";
            }
            case PENDENCIA -> {
                tipo = TipoItemFila.ONBOARDING_PENDENTE;
                prioridade = PrioridadeItem.MEDIA;
                titulo = "Onboarding com pendencia documental";
            }
            default -> {
                return;
            }
        }

        criarItem.criarSeAusente(new CriarItemCommand(
                tipo,
                prioridade,
                TipoEntidadeReferenciada.ONBOARDING,
                event.solicitacaoId(),
                titulo,
                "Verificacao externa: " + event.idVerificacaoExterna()));
    }
}

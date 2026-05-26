package com.dynamis.sep_api.backoffice.application.listener;

import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService.CriarItemCommand;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.cobranca.domain.event.ParcelaInadimplenteEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cria item de fila prioridade ALTA quando parcela vira {@code INADIMPLENTE} (Sprint 14 Task 14.2).
 * Consome {@link ParcelaInadimplenteEvent} publicado pelo job de inadimplencia da Sprint 13.
 */
@Component
public class ParcelaInadimplenteListener {

    private final CriarItemFilaOperacionalService criarItem;

    public ParcelaInadimplenteListener(CriarItemFilaOperacionalService criarItem) {
        this.criarItem = criarItem;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoMarcarInadimplente(ParcelaInadimplenteEvent event) {
        String titulo = "Parcela " + event.numero() + " inadimplente ha " + event.diasAtraso() + " dias";
        String descricao = "Contrato " + event.contratoId() + " / agenda " + event.agendaId();

        criarItem.criarSeAusente(new CriarItemCommand(
                TipoItemFila.COBRANCA_INADIMPLENTE,
                PrioridadeItem.ALTA,
                TipoEntidadeReferenciada.PARCELA_COBRANCA,
                event.parcelaId(),
                titulo,
                descricao));
    }
}

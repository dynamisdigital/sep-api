package com.dynamis.sep_api.backoffice.application.listener;

import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService.CriarItemCommand;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.pix.domain.event.PixTransferenciaFalhouEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event-driven (Sprint 20 Task 20.4): uma falha de desembolso Pix gera item operacional para o
 * backoffice tratar. Consome {@link PixTransferenciaFalhouEvent} apos o commit da transacao que o
 * publicou ({@code AFTER_COMMIT}) — a falha so vira pendencia se ficou de fato persistida.
 *
 * <p>A criacao do item eh idempotente (UNIQUE parcial por entidade ativa) e roda em
 * {@code REQUIRES_NEW} dentro do {@link CriarItemFilaOperacionalService} — nao reabre a tx ja
 * commitada.
 */
@Component
public class DesembolsoPixFalhouListener {

    private static final int MAX_MOTIVO = 200;

    private final CriarItemFilaOperacionalService criarItem;

    public DesembolsoPixFalhouListener(CriarItemFilaOperacionalService criarItem) {
        this.criarItem = criarItem;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoFalhar(PixTransferenciaFalhouEvent evento) {
        criarItem.criarSeAusente(new CriarItemCommand(
                TipoItemFila.DESEMBOLSO_PIX_FALHOU,
                PrioridadeItem.ALTA,
                TipoEntidadeReferenciada.PIX_TRANSFERENCIA,
                evento.transferenciaId(),
                "Desembolso Pix falhou",
                truncar(evento.motivo())));
    }

    private String truncar(String motivo) {
        if (motivo == null) {
            return "Falha no desembolso Pix";
        }
        return motivo.length() > MAX_MOTIVO ? motivo.substring(0, MAX_MOTIVO) : motivo;
    }
}

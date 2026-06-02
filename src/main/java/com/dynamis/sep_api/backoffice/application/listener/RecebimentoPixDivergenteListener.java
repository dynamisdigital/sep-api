package com.dynamis.sep_api.backoffice.application.listener;

import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService.CriarItemCommand;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.pix.domain.event.PixRecebimentoDivergenteEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event-driven (Sprint 21 Task 21.5): uma divergencia de recebimento Pix (referencia desconhecida,
 * nao-ATIVA, {@code endToEndId} ausente, valor parcial/maior ou falha de baixa) gera item operacional
 * para o backoffice tratar. Consome {@link PixRecebimentoDivergenteEvent} apos o commit da transacao
 * que o publicou ({@code AFTER_COMMIT}) — a divergencia so vira pendencia se ficou de fato persistida.
 *
 * <p>A criacao do item eh idempotente (UNIQUE parcial por entidade ativa) e roda em
 * {@code REQUIRES_NEW} dentro do {@link CriarItemFilaOperacionalService} — varios eventos para o mesmo
 * recebimento colapsam num unico item ativo.
 */
@Component
public class RecebimentoPixDivergenteListener {

    private static final int MAX_MOTIVO = 200;

    private final CriarItemFilaOperacionalService criarItem;

    public RecebimentoPixDivergenteListener(CriarItemFilaOperacionalService criarItem) {
        this.criarItem = criarItem;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoDivergir(PixRecebimentoDivergenteEvent evento) {
        criarItem.criarSeAusente(new CriarItemCommand(
                TipoItemFila.RECEBIMENTO_PIX_DIVERGENTE,
                PrioridadeItem.ALTA,
                TipoEntidadeReferenciada.PIX_RECEBIMENTO,
                evento.recebimentoId(),
                "Recebimento Pix divergente",
                truncar(evento.motivo())));
    }

    private String truncar(String motivo) {
        if (motivo == null) {
            return "Recebimento Pix divergente";
        }
        return motivo.length() > MAX_MOTIVO ? motivo.substring(0, MAX_MOTIVO) : motivo;
    }
}

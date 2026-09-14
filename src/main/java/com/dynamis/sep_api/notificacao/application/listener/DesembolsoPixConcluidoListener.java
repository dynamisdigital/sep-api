package com.dynamis.sep_api.notificacao.application.listener;

import com.dynamis.sep_api.notificacao.application.usecase.NotificarUsuarioUseCase;
import com.dynamis.sep_api.notificacao.domain.vo.ConteudoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.OrigemNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.Referencia;
import com.dynamis.sep_api.notificacao.domain.vo.TipoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.TipoReferencia;
import com.dynamis.sep_api.pix.domain.event.PixTransferenciaConcluidaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Avisa o tomador, na central, que o desembolso Pix concluiu (Sprint 38 Task 38.6, ADR 0021). E o
 * primeiro momento positivo em que o SEP fala com o tomador.
 *
 * <p>{@code AFTER_COMMIT}: transferencia cuja conclusao nao comitou nao gera aviso. A notificacao grava
 * em transacao propria e qualquer falha dela para aqui — o desembolso ja esta concluido, e nada deste
 * listener pode desfaze-lo.
 *
 * <p>Allowlist (ADR 0021 §7): do evento so saem o tomador, o id da transferencia como origem e o
 * contrato como referencia. O {@code externalId} do provider nunca e lido.
 */
@Component
public class DesembolsoPixConcluidoListener {

    static final String TITULO = "Desembolso concluido";
    static final String MENSAGEM = "A transferencia Pix do desembolso do seu contrato foi concluida.";

    private static final Logger log = LoggerFactory.getLogger(DesembolsoPixConcluidoListener.class);

    private final NotificarUsuarioUseCase notificar;

    public DesembolsoPixConcluidoListener(NotificarUsuarioUseCase notificar) {
        this.notificar = notificar;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoConcluir(PixTransferenciaConcluidaEvent evento) {
        if (evento.tomadorId() == null) {
            return;
        }
        try {
            notificar.disponibilizarNaCentral(evento.tomadorId(), origem(evento), conteudo(evento));
        } catch (RuntimeException falha) {
            log.atError()
                    .addKeyValue("event", "notification_not_recorded")
                    .addKeyValue("tipo", TipoNotificacao.DESEMBOLSO_PIX_CONCLUIDO)
                    .addKeyValue("motivo", falha.getClass().getName())
                    .log("Notificacao de desembolso Pix concluido nao registrada");
        }
    }

    static OrigemNotificacao origem(PixTransferenciaConcluidaEvent evento) {
        return new OrigemNotificacao(
                TipoNotificacao.DESEMBOLSO_PIX_CONCLUIDO,
                evento.transferenciaId().toString());
    }

    static ConteudoNotificacao conteudo(PixTransferenciaConcluidaEvent evento) {
        Referencia contrato =
                evento.contratoId() == null ? null : new Referencia(TipoReferencia.CONTRATO, evento.contratoId());
        return new ConteudoNotificacao(TITULO, MENSAGEM, contrato);
    }
}

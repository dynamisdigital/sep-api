package com.dynamis.sep_api.notificacao.application.listener;

import com.dynamis.sep_api.identity.domain.event.ContaBloqueadaEvent;
import com.dynamis.sep_api.notificacao.application.usecase.NotificarUsuarioUseCase;
import com.dynamis.sep_api.notificacao.domain.vo.ConteudoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.OrigemNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.TipoNotificacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * E-mail de conta bloqueada (Sprint 38 Task 38.5, ADR 0021 §1). Assunto e corpo sao os mesmos que o
 * {@code LockoutService} enviava pelo antigo {@code EmailService}; a diferenca e que agora a tentativa
 * fica no historico.
 *
 * <p>{@code AFTER_COMMIT}: bloqueio cujo audit nao comitou nao gera e-mail. A notificacao grava em
 * transacao propria, e qualquer falha dela para aqui — o login ja respondeu pelo bloqueio, e nada
 * deste listener pode mudar isso.
 */
@Component
public class ContaBloqueadaListener {

    static final String ASSUNTO = "Conta SEP bloqueada temporariamente";

    private static final Logger log = LoggerFactory.getLogger(ContaBloqueadaListener.class);

    private final NotificarUsuarioUseCase notificar;

    public ContaBloqueadaListener(NotificarUsuarioUseCase notificar) {
        this.notificar = notificar;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoBloquear(ContaBloqueadaEvent evento) {
        try {
            notificar.enviarEmail(evento.usuarioId(), evento.username(), origem(evento), conteudo(evento));
        } catch (RuntimeException falha) {
            log.atError()
                    .addKeyValue("event", "notification_not_recorded")
                    .addKeyValue("tipo", TipoNotificacao.CONTA_BLOQUEADA)
                    .addKeyValue("motivo", falha.getClass().getName())
                    .log("Notificacao de conta bloqueada nao registrada");
        }
    }

    /** O instante da falha que fechou a janela identifica o bloqueio, em UTC para ser estavel. */
    static OrigemNotificacao origem(ContaBloqueadaEvent evento) {
        return new OrigemNotificacao(
                TipoNotificacao.CONTA_BLOQUEADA,
                evento.bloqueadaEm().toInstant().toString());
    }

    static ConteudoNotificacao conteudo(ContaBloqueadaEvent evento) {
        return new ConteudoNotificacao(
                ASSUNTO,
                "Detectamos varias tentativas de login. Sua conta esta bloqueada por " + evento.lockoutMinutes()
                        + " minutos.",
                null);
    }
}

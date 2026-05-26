package com.dynamis.sep_api.cobranca.application.port.out;

import com.dynamis.sep_api.cobranca.application.port.out.dto.Notificacao;
import com.dynamis.sep_api.cobranca.application.port.out.dto.ResultadoNotificacao;
import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;

/**
 * Port de envio de notificacoes transacionais (Sprint 13 - ADR 0014).
 *
 * <p>Cada provider declara quais {@link CanalNotificacao} suporta via {@link #suporta(CanalNotificacao)};
 * o use case que dispatch (Task 13.4) injeta {@code List<NotificationProvider>} e escolhe pela
 * combinacao canal + ambiente. Em ambientes dev/test o {@code LogNotificationProvider} unico
 * cobre todos os canais; em prod {@code SmtpNotificationProvider} cobre EMAIL e
 * {@code ZenviaSmsNotificationProvider} cobre SMS.
 *
 * <p>Implementacoes devem ser idempotentes a nivel de adapter quando o provider externo aceitar
 * chave (Zenvia aceita request id). A idempotencia de dominio (nao enviar duplicado no mesmo dia)
 * vive em {@code EventoCobranca} + unique parcial.
 */
public interface NotificationProvider {

    ResultadoNotificacao enviar(Notificacao notificacao);

    boolean suporta(CanalNotificacao canal);

    /** Identificador tecnico do provider (e.g. {@code "log"}, {@code "smtp"}, {@code "zenvia"}). */
    String nome();
}

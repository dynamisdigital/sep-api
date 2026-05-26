package com.dynamis.sep_api.cobranca.infrastructure.adapter.notification;

import com.dynamis.sep_api.cobranca.application.port.out.NotificationProvider;
import com.dynamis.sep_api.cobranca.application.port.out.dto.Notificacao;
import com.dynamis.sep_api.cobranca.application.port.out.dto.ResultadoNotificacao;
import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter de log default (Sprint 13 - ADR 0014).
 *
 * <p>Ativado quando {@code app.notificacoes.provider=log} (default). Registra metadados da
 * notificacao sem renderizar conteudo nem enviar nada externo. Mantem dev/test/local-wiremock
 * deterministicos sem depender de SMTP ou Zenvia.
 *
 * <p>Suporta ambos os canais. Em modo {@code smtp-zenvia} este bean nao eh registrado;
 * SmtpNotificationProvider + ZenviaSmsNotificationProvider tomam o lugar.
 */
@Component
@ConditionalOnProperty(name = "app.notificacoes.provider", havingValue = "log", matchIfMissing = true)
public class LogNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationProvider.class);
    private static final String NOME = "log";

    @Override
    public ResultadoNotificacao enviar(Notificacao notificacao) {
        log.info(
                "[LogNotificationProvider] canal={} template={} destinatario_mascarado={} variaveis_chaves={} correlationId={}",
                notificacao.canal(),
                notificacao.template(),
                mascarar(notificacao.destinatario(), notificacao.canal()),
                notificacao.variaveis().keySet(),
                notificacao.correlationId());
        return ResultadoNotificacao.sucesso(NOME, null);
    }

    @Override
    public boolean suporta(CanalNotificacao canal) {
        return true;
    }

    @Override
    public String nome() {
        return NOME;
    }

    /**
     * Mascara destinatario antes de logar — defesa em profundidade (LGPD). Email mostra primeira
     * letra + dominio; telefone mostra ultimos 4 digitos.
     */
    private static String mascarar(String destinatario, CanalNotificacao canal) {
        return switch (canal) {
            case EMAIL -> mascararEmail(destinatario);
            case SMS -> mascararTelefone(destinatario);
        };
    }

    private static String mascararEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***@***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String mascararTelefone(String telefone) {
        if (telefone.length() < 4) {
            return "****";
        }
        return "****" + telefone.substring(telefone.length() - 4);
    }
}

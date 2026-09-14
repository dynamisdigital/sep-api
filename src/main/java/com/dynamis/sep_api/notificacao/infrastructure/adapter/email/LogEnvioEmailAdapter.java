package com.dynamis.sep_api.notificacao.infrastructure.adapter.email;

import com.dynamis.sep_api.notificacao.application.port.out.EnvioEmailPort;
import com.dynamis.sep_api.notificacao.application.port.out.dto.EmailNotificacao;
import com.dynamis.sep_api.notificacao.application.port.out.dto.ResultadoEnvioEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Unico adapter de e-mail do modulo de notificacao (Sprint 38; estrategia de providers do ADR 0014).
 *
 * <p>Registra que houve uma tentativa, sem destinatario, assunto nem corpo, e devolve {@link
 * ResultadoEnvioEmail#SIMULADO}: o historico grava {@code SIMULADA} e nunca confunde simulacao com
 * entrega.
 *
 * <p>Ativo em qualquer valor de {@code app.notificacoes.provider}, como o {@code LogEmailService} que
 * substitui. Nao ha adapter real neste modulo ainda; condicionar este bean a {@code provider=log}
 * impediria a aplicacao de subir com {@code smtp-zenvia}, que a cobranca ja usa. Quando o adapter
 * real existir, a selecao por essa chave entra junto com ele.
 */
@Component
public class LogEnvioEmailAdapter implements EnvioEmailPort {

    private static final Logger log = LoggerFactory.getLogger(LogEnvioEmailAdapter.class);

    @Override
    public ResultadoEnvioEmail enviar(EmailNotificacao email) {
        log.atInfo()
                .addKeyValue("event", "notification_simulated")
                .addKeyValue("channel", "email")
                .log("Envio de email simulado");
        return ResultadoEnvioEmail.SIMULADO;
    }
}

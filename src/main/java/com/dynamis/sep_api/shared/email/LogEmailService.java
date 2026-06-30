package com.dynamis.sep_api.shared.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementacao de {@link EmailService} para dev-local (Sprint 5 Task 5.4). Registra a tentativa de
 * envio em log, sem dependencia de SMTP real. Substituir por adapter real (SES, SMTP, etc.) em
 * producao.
 */
@Component
public class LogEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LogEmailService.class);

    @Override
    public void enviar(String para, String assunto, String corpo) {
        log.atInfo()
                .addKeyValue("event", "notification_simulated")
                .addKeyValue("channel", "email")
                .log("Envio de email simulado");
    }
}

package com.dynamis.sep_api.shared.email;

/**
 * Port de envio de e-mail (Sprint 5 Task 5.4). Implementacao concreta pode integrar SES/SMTP/etc;
 * no dev-local o adapter {@link LogEmailService} apenas registra em log para nao depender de SMTP.
 */
public interface EmailService {

    void enviar(String para, String assunto, String corpo);
}

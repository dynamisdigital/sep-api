package com.dynamis.sep_api.cobranca.infrastructure.adapter.notification;

/** Falha na renderizacao de um template de notificacao (Sprint 13 Task 13.3). */
public class TemplateNotificacaoException extends RuntimeException {

    public TemplateNotificacaoException(String message) {
        super(message);
    }

    public TemplateNotificacaoException(String message, Throwable cause) {
        super(message, cause);
    }
}

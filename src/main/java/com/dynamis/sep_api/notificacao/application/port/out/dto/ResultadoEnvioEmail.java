package com.dynamis.sep_api.notificacao.application.port.out.dto;

/** Desfecho de um envio de e-mail que nao lancou excecao (ADR 0021 §3). */
public enum ResultadoEnvioEmail {
    /** Provider real aceitou a mensagem. */
    ENVIADO,
    /** Adapter de log: nada saiu do processo, e o registro nunca vale como comprovante. */
    SIMULADO
}

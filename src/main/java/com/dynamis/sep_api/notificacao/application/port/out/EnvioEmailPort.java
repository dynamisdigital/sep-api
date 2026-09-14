package com.dynamis.sep_api.notificacao.application.port.out;

import com.dynamis.sep_api.notificacao.application.port.out.dto.EmailNotificacao;
import com.dynamis.sep_api.notificacao.application.port.out.dto.ResultadoEnvioEmail;

/**
 * Envio de e-mail do modulo de notificacao (Sprint 38, ADR 0021 §1; providers do ADR 0014).
 *
 * <p>Porta do proprio modulo: nao reusa o {@code NotificationProvider} da cobranca. Falha de envio
 * sobe como excecao; quem chama registra {@code FALHOU} com motivo sanitizado.
 */
public interface EnvioEmailPort {

    /**
     * @return {@link ResultadoEnvioEmail#ENVIADO} quando um provider real aceitou a mensagem, ou
     *     {@link ResultadoEnvioEmail#SIMULADO} quando nada saiu do processo. Nenhum dos dois e ciencia do
     *     destinatario.
     */
    ResultadoEnvioEmail enviar(EmailNotificacao email);
}

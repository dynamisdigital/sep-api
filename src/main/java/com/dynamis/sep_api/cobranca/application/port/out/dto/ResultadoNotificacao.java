package com.dynamis.sep_api.cobranca.application.port.out.dto;

import com.dynamis.sep_api.cobranca.domain.vo.StatusEventoCobranca;

import java.util.Objects;

/**
 * Resultado do envio de uma {@link Notificacao} (Sprint 13).
 *
 * <p>Adapters devem retornar resultado para falhas tecnicas tratadas (4xx validavel, payload
 * invalido pelo provider, destinatario invalido detectado antes do envio). Falhas de
 * infraestrutura (timeout, 5xx) podem subir como excecao — o use case decide se gera evento de
 * falha ou propaga.
 */
public record ResultadoNotificacao(
        StatusEventoCobranca status, String providerNome, String idExterno, String mensagemTecnica) {

    public ResultadoNotificacao {
        Objects.requireNonNull(status, "status obrigatorio");
        Objects.requireNonNull(providerNome, "providerNome obrigatorio");
    }

    public static ResultadoNotificacao sucesso(String providerNome, String idExterno) {
        return new ResultadoNotificacao(StatusEventoCobranca.SUCESSO, providerNome, idExterno, null);
    }

    public static ResultadoNotificacao falha(String providerNome, String mensagemTecnica) {
        return new ResultadoNotificacao(StatusEventoCobranca.FALHA, providerNome, null, mensagemTecnica);
    }
}

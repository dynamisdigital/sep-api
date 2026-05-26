package com.dynamis.sep_api.cobranca.domain.vo;

/**
 * Status de processamento de um {@code EventoCobranca} (Sprint 13 Task 13.2).
 *
 * <p>{@code SUCESSO} e {@code FALHA} aplicam-se principalmente a {@code NOTIFICACAO_AUTOMATICA}.
 * Eventos sem semantica de entrega externa (contato manual, renegociacao, marcacao inadimplente)
 * nascem em {@code SUCESSO}.
 */
public enum StatusEventoCobranca {
    SUCESSO,
    FALHA
}

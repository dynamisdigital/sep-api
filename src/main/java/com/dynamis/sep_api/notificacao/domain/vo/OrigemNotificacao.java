package com.dynamis.sep_api.notificacao.domain.vo;

import java.util.Objects;

/**
 * Fato que originou a notificacao: tipo mais identificador derivado do fato, nunca aleatorio
 * (ADR 0021 §4). Com o destinatario, forma a chave de idempotencia.
 */
public record OrigemNotificacao(TipoNotificacao tipo, String id) {

    static final int TAMANHO_MAXIMO_ID = 100;

    public OrigemNotificacao {
        Objects.requireNonNull(tipo, "tipo obrigatorio");
        Objects.requireNonNull(id, "id obrigatorio");
        if (id.isBlank() || id.length() > TAMANHO_MAXIMO_ID) {
            throw new IllegalArgumentException("id da origem deve ter entre 1 e " + TAMANHO_MAXIMO_ID + " caracteres");
        }
    }
}

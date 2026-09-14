package com.dynamis.sep_api.notificacao.application.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

/**
 * Ausencia neutra (HTTP 404) na central (ADR 0021 §9). Notificacao inexistente, de outro usuario ou
 * fora do recorte {@code IN_APP} lancam esta mesma excecao, sem identificador, para nao revelar a
 * existencia de notificacao alheia.
 */
public class NotificacaoNaoEncontradaException extends RecursoNaoEncontradoException {

    public NotificacaoNaoEncontradaException() {
        super("NTF-404-001", "Notificacao nao encontrada");
    }
}

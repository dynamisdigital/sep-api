package com.dynamis.sep_api.backoffice.domain.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

import java.util.UUID;

/** Item da fila operacional buscado nao existe (HTTP 404). */
public class ItemFilaNaoEncontradoException extends RecursoNaoEncontradoException {

    public static final String CODIGO = "BOF-404-001";

    public ItemFilaNaoEncontradoException(UUID itemId) {
        super(CODIGO, "Item de fila nao encontrado: " + itemId);
    }
}

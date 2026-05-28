package com.dynamis.sep_api.credores.domain.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

import java.util.UUID;

/** Empresa credora inexistente (HTTP 404). */
public class EmpresaCredoraNaoEncontradaException extends RecursoNaoEncontradoException {

    public static final String CODIGO = "CRD-404-001";

    public EmpresaCredoraNaoEncontradaException(UUID id) {
        super(CODIGO, "Empresa credora " + id + " nao encontrada");
    }

    private EmpresaCredoraNaoEncontradaException(String mensagem) {
        super(CODIGO, mensagem);
    }

    public static EmpresaCredoraNaoEncontradaException porUsuario(UUID usuarioId) {
        return new EmpresaCredoraNaoEncontradaException("Usuario " + usuarioId + " nao possui empresa credora");
    }
}

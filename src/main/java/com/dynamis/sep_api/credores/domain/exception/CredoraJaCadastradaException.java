package com.dynamis.sep_api.credores.domain.exception;

import com.dynamis.sep_api.shared.exception.ConflitoException;

/** Empresa credora ja cadastrada para o usuario, onboarding ou CNPJ informado (HTTP 409). */
public class CredoraJaCadastradaException extends ConflitoException {

    public static final String CODIGO = "CRD-409-001";

    private CredoraJaCadastradaException(String mensagem) {
        super(CODIGO, mensagem);
    }

    public static CredoraJaCadastradaException porUsuario() {
        return new CredoraJaCadastradaException("Usuario ja possui empresa credora cadastrada");
    }

    public static CredoraJaCadastradaException porOnboarding() {
        return new CredoraJaCadastradaException("Onboarding ja vinculado a uma empresa credora");
    }

    public static CredoraJaCadastradaException porCnpj() {
        return new CredoraJaCadastradaException("CNPJ ja cadastrado como empresa credora");
    }
}

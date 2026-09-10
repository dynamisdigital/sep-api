package com.dynamis.sep_api.pix.domain.exception;

import com.dynamis.sep_api.shared.exception.ConflitoException;

/**
 * Idempotency-Key ja usada com outro payload num comando Pix (HTTP 409).
 *
 * <p>Uma condicao so (ADR 0020 §3, decisao da Sprint 37): em desembolso e em cadastro de chave o
 * cliente faz a mesma coisa — reenvia o payload original ou usa uma chave nova. Antes eram dois
 * codigos semanticos ({@code PIX-409-IDEMPOTENCIA} e {@code PIX-409-IDEMPOTENCIA-CHAVE}); a mensagem
 * de cada operacao continua a mesma. O {@code cobranca} tem a condicao publicada como
 * {@code COB-409-004}; unificar entre modulos e mudanca de contrato, fora da Sprint 37.
 */
public class IdempotencyKeyConflitanteException extends ConflitoException {

    public static final String CODIGO = "PIX-409-004";

    private IdempotencyKeyConflitanteException(String mensagem) {
        super(CODIGO, mensagem);
    }

    public static IdempotencyKeyConflitanteException desembolso(String idempotencyKey) {
        return new IdempotencyKeyConflitanteException(
                "Idempotency-Key '" + idempotencyKey + "' ja foi usada com contrato/valor/chave diferentes.");
    }

    public static IdempotencyKeyConflitanteException cadastroDeChave(String idempotencyKey) {
        return new IdempotencyKeyConflitanteException(
                "Idempotency-Key '" + idempotencyKey + "' ja foi usada com tipo/valor de chave diferentes.");
    }
}

package com.dynamis.sep_api.pix.domain.exception;

import com.dynamis.sep_api.shared.exception.ValidacaoException;

/**
 * Idempotency-Key acima de 100 caracteres num comando Pix (HTTP 400): uma condicao para desembolso e
 * cadastro de chave (ADR 0020 §3).
 *
 * <p>O {@code credores} tem a mesma condicao publicada como {@code CRD-400-004}. Unificar entre
 * modulos exige renomear codigo publicado — mudanca de contrato, fora da Sprint 37.
 */
public class IdempotencyKeyMuitoLongaException extends ValidacaoException {

    public static final String CODIGO = "PIX-400-007";

    public IdempotencyKeyMuitoLongaException() {
        super(CODIGO, "Idempotency-Key nao pode exceder 100 caracteres.");
    }
}

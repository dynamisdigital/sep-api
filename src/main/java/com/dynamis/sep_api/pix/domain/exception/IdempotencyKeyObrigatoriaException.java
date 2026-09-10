package com.dynamis.sep_api.pix.domain.exception;

import com.dynamis.sep_api.shared.exception.ValidacaoException;

/**
 * Comando Pix sem Idempotency-Key (HTTP 400): uma condicao para desembolso e cadastro de chave (ADR
 * 0020 §3).
 *
 * <p>O {@code credores} tem a mesma condicao publicada como {@code CRD-400-003}. Unificar entre
 * modulos exige renomear codigo publicado — mudanca de contrato, fora da Sprint 37.
 */
public class IdempotencyKeyObrigatoriaException extends ValidacaoException {

    public static final String CODIGO = "PIX-400-006";

    public IdempotencyKeyObrigatoriaException() {
        super(CODIGO, "Idempotency-Key obrigatoria.");
    }
}

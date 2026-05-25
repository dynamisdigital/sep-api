package com.dynamis.sep_api.cobranca.domain.exception;

import com.dynamis.sep_api.shared.exception.ConflitoException;

import java.util.UUID;

/**
 * Ja existe renegociacao PROPOSTA ativa pra parcela (Sprint 13 Task 13.6). Protegido tambem por
 * unique parcial {@code uq_renegociacao_parcela_ativa}. HTTP 409.
 */
public class RenegociacaoConflitanteException extends ConflitoException {

    public static final String CODIGO = "COB-409-002";

    public RenegociacaoConflitanteException(UUID parcelaId) {
        super(CODIGO, "Ja existe renegociacao ativa para parcela " + parcelaId);
    }
}

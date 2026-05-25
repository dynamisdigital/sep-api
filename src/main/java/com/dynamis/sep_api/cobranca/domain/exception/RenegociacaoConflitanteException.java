package com.dynamis.sep_api.cobranca.domain.exception;

import java.util.UUID;

/**
 * Lancada quando ja existe renegociacao PROPOSTA ativa pra parcela (Sprint 13 Task 13.6).
 * Protegido tambem por unique parcial {@code uq_renegociacao_parcela_ativa}.
 */
public class RenegociacaoConflitanteException extends RuntimeException {

    public RenegociacaoConflitanteException(UUID parcelaId) {
        super("ja existe renegociacao ativa para parcela " + parcelaId);
    }
}

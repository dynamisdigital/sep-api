package com.dynamis.sep_api.cobranca.domain.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

import java.util.UUID;

/** Renegociacao buscada nao existe (HTTP 404). */
public class RenegociacaoNaoEncontradaException extends RecursoNaoEncontradoException {

    public static final String CODIGO = "COB-404-003";

    public RenegociacaoNaoEncontradaException(UUID renegociacaoId) {
        super(CODIGO, "Renegociacao nao encontrada: " + renegociacaoId);
    }

    private RenegociacaoNaoEncontradaException(String mensagem) {
        super(CODIGO, mensagem);
    }

    /**
     * Consulta owner-scoped do tomador (Sprint 24): parcela propria sem proposta ativa. Mensagem
     * generica sem UUID de parcela/contrato/tomador/renegociacao (spec 024 — nao expor identificador
     * interno no corpo do 404).
     */
    public static RenegociacaoNaoEncontradaException semPropostaAtiva() {
        return new RenegociacaoNaoEncontradaException("Nenhuma renegociacao ativa para a parcela informada");
    }
}

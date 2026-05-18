package com.dynamis.sep_api.shared.exception;

/**
 * Excecao de dominio para violacao de ownership/autorizacao em recurso (HTTP 403). Marcada como
 * {@code non-sealed} para permitir subtipos por modulo, como {@code OwnershipPropostaException}.
 *
 * <p>Diferente de {@link org.springframework.security.access.AccessDeniedException} (que cobre o
 * filtro Spring Security), esta excecao e lancada pela camada de aplicacao apos uma validacao
 * explicita de ownership em regra de negocio (ex.: tomador tenta criar proposta usando onboarding
 * de outro tomador).
 */
public non-sealed class AcessoNegadoException extends DomainException {

    public AcessoNegadoException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }
}

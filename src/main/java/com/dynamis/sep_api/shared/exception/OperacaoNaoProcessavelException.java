package com.dynamis.sep_api.shared.exception;

/**
 * Excecao de dominio para pre-condicao de negocio nao atendida (HTTP 422 Unprocessable Entity).
 * Marcada como {@code non-sealed} para permitir subtipos por modulo, como {@code
 * OnboardingNaoAprovadoException}.
 *
 * <p>Usar 422 quando o payload e sintaticamente valido (vs 400) e o recurso existe (vs 404), mas
 * a operacao nao pode ser executada por uma regra de dominio — ex.: criar proposta exige
 * onboarding em {@code APROVADO_FINAL}.
 */
public non-sealed class OperacaoNaoProcessavelException extends DomainException {

    public OperacaoNaoProcessavelException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }
}

package com.dynamis.sep_api.shared.exception;

/**
 * Tipo base (sealed) das excecoes de dominio do SEP.
 *
 * <p>Sprints futuras criam subtipos especificos (ex.: {@code UsuarioNaoEncontrado}, {@code
 * SenhaInvalida}, {@code OnboardingPendenteException}). Esta sealed hierarchy forca que o
 * {@link ApiExceptionHandler} mapeie todos os casos via {@code switch} exhaustivo a partir da
 * Sprint 4.
 */
public abstract sealed class DomainException extends RuntimeException
        permits ValidacaoException, RecursoNaoEncontradoException, ConflitoException {

    private final String codigo;

    protected DomainException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    protected DomainException(String codigo, String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}

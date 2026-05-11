package com.dynamis.sep_api.identity.application.exception;

/**
 * Sinaliza que a conta excedeu o numero de tentativas de login falhas dentro da janela e esta
 * temporariamente bloqueada (Sprint 5 Task 5.4).
 *
 * <p>Mapeada para HTTP {@code 423 Locked} pelo {@code ApiExceptionHandler}.
 */
public final class ContaBloqueadaException extends RuntimeException {

    public static final String CODIGO = "AUTH-423-001";

    public ContaBloqueadaException(int lockoutMinutes) {
        super("Conta bloqueada temporariamente. Tente novamente em " + lockoutMinutes + " minutos.");
    }

    public String getCodigo() {
        return CODIGO;
    }
}

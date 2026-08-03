package com.dynamis.sep_api.identity.application.exception;

/**
 * Sinaliza que a conta excedeu o numero de tentativas de login falhas dentro da janela e esta
 * temporariamente bloqueada (Sprint 5 Task 5.4).
 *
 * <p>Mapeada para HTTP {@code 423 Locked} pelo {@code ApiExceptionHandler}.
 */
public final class ContaBloqueadaException extends RuntimeException {

    public static final String CODIGO = "AUTH-423-001";

    private final long segundosRestantes;

    /**
     * @param lockoutMinutes duracao <b>configurada</b> do bloqueio, que a mensagem publica como
     *     enunciado da politica
     * @param segundosRestantes quanto falta <b>deste</b> bloqueio, publicado no {@code Retry-After}
     *     (Sprint 34 Task 34.3). Os dois diferem: um bloqueio que ja correu metade do tempo anuncia
     *     a mesma politica e uma espera menor.
     */
    public ContaBloqueadaException(int lockoutMinutes, long segundosRestantes) {
        super("Conta bloqueada temporariamente. Tente novamente em " + lockoutMinutes + " minutos.");
        this.segundosRestantes = segundosRestantes;
    }

    public long getSegundosRestantes() {
        return segundosRestantes;
    }

    public String getCodigo() {
        return CODIGO;
    }
}

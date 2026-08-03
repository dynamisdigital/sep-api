package com.dynamis.sep_api.identity.application.exception;

import java.time.Duration;

/**
 * Sinaliza que a conta excedeu o numero de tentativas de login falhas dentro da janela e esta
 * temporariamente bloqueada (Sprint 5 Task 5.4).
 *
 * <p>Mapeada para HTTP {@code 423 Locked} pelo {@code ApiExceptionHandler}.
 */
public final class ContaBloqueadaException extends RuntimeException {

    public static final String CODIGO = "AUTH-423-001";

    private final Duration tempoRestante;

    /**
     * @param lockoutMinutes duracao <b>configurada</b> do bloqueio, que a mensagem publica como
     *     enunciado da politica
     * @param tempoRestante quanto falta <b>deste</b> bloqueio (Sprint 34 Task 34.3). Os dois diferem:
     *     um bloqueio que ja correu metade do tempo anuncia a mesma politica e uma espera menor.
     *     <p>E {@link Duration}, e nao segundos, por dois motivos: arredondar para o inteiro do
     *     {@code Retry-After} e regra de transporte e pertence ao handler; e dois numeros adjacentes
     *     em unidades diferentes, com {@code int} alargando para {@code long} em silencio, deixariam
     *     {@code (1800, 30)} compilar limpo e produzir "Tente novamente em 1800 minutos".
     */
    public ContaBloqueadaException(int lockoutMinutes, Duration tempoRestante) {
        super("Conta bloqueada temporariamente. Tente novamente em " + lockoutMinutes + " minutos.");
        this.tempoRestante = tempoRestante;
    }

    public Duration getTempoRestante() {
        return tempoRestante;
    }

    public String getCodigo() {
        return CODIGO;
    }
}

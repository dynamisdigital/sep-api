package com.dynamis.sep_api.identity.application.exception;

import java.time.Duration;

/**
 * Sinaliza que a conta excedeu o numero de tentativas de login falhas dentro da janela e esta
 * temporariamente bloqueada (Sprint 5 Task 5.4).
 *
 * <p>Mapeada para HTTP {@code 423 Locked} pelo {@code ApiExceptionHandler}.
 */
public final class ContaBloqueadaException extends RuntimeException {

    /**
     * Codigo de erro estavel do bloqueio de conta.
     *
     * <p><b>Por que esta classe carrega constante e {@link #getCodigo()} proprios em vez de herdar
     * o {@code codigo}</b>: {@link com.dynamis.sep_api.shared.exception.DomainException} e
     * {@code sealed} e nao a permite, entao {@code ContaBloqueadaException} fica fora daquela
     * hierarquia e precisa do par. Isto vale independentemente de sprint.
     *
     * <p><b>Sem consumidor em {@code src/main} ate a Sprint 36 Task 36.4</b>, que publica o codigo
     * no corpo do {@code 423}: o {@code handleLocked} le por {@link #getCodigo()} e o {@code build()}
     * propaga. A Task 35.5 chegou a planejar a remocao por "nao ha consumidor", e a Spec 036
     * §Conflito cancelou. Criterio que fica: sem consumidor <b>e</b> sem spec publicada que lhe de
     * um — so o primeiro nao basta.
     */
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

    /** Ver a nota em {@link #CODIGO}: e por este getter que a Sprint 36 le o codigo. */
    public String getCodigo() {
        return CODIGO;
    }
}

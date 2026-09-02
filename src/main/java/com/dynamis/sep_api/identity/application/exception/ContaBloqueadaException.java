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
     * Codigo de erro estavel do bloqueio de conta. <b>Sem consumidor em {@code src/main} — de
     * proposito, e isto nao e codigo morto.</b>
     *
     * <p>A Sprint 35 Task 35.5 chegou a planejar a remocao, com a justificativa correta para o
     * momento em que foi escrita: nao havia consumidor. A Spec 036 §Conflito cancelou a remocao — a
     * Sprint 36 publica {@code codigo} no corpo do erro (Task 36.4) e o {@code build()} do
     * {@code ApiExceptionHandler} le daqui. Remover para recriar duas sprints depois e churn que
     * paga dois ciclos de PR.
     *
     * <p>Este comentario existe porque o levantamento ja classificou a constante como morta uma vez,
     * pelo criterio de "sem consumidor hoje". O criterio correto e outro: sem consumidor <b>e</b> sem
     * spec publicada que lhe de um.
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

    public String getCodigo() {
        return CODIGO;
    }
}

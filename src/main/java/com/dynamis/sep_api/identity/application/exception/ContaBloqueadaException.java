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
     * @param tempoRestante quanto falta <b>deste</b> bloqueio. A mensagem publica esse valor, e nao a
     *     duracao configurada da politica (Sprint 35 Task 35.7).
     *     <p>Ate a Sprint 34 o construtor recebia os <b>dois</b> numeros e a mensagem anunciava a
     *     politica, com o argumento de que politica e restante sao fatos diferentes. Sao mesmo — mas
     *     a frase nao enuncia politica, ela <b>manda o usuario esperar</b>: "Tente novamente em 30
     *     minutos" com 2 minutos restantes pede uma espera 15x maior que a real. Quem le so o corpo
     *     (integracoes, e o {@code sep-mobile} quando propagar a mensagem) nao tem como saber disso.
     *     <p>Medido em 2026-09-02: <b>nenhum consumidor exibe esta mensagem hoje</b> — o
     *     {@code sep-app} reescreve a frase a partir do {@code Retry-After} e o {@code sep-mobile}
     *     descarta o {@code HttpErrorResponse} na navegacao. Alinhar nao muda tela nenhuma agora; o
     *     valor esta em nao deixar uma afirmacao falsa no contrato.
     *     <p>Com um numero so, o risco de tipo que motivava o par tambem some: nao ha mais como
     *     trocar minutos por segundos entre dois argumentos adjacentes.
     */
    public ContaBloqueadaException(Duration tempoRestante) {
        super(mensagemDe(tempoRestante));
        this.tempoRestante = tempoRestante;
    }

    /**
     * Arredonda <b>para cima</b>, pelo mesmo motivo do {@code Retry-After} no handler: informar menos
     * do que falta convida o usuario a voltar ainda dentro do bloqueio. Os dois numeros ficam
     * coerentes por construcao.
     */
    private static String mensagemDe(Duration tempoRestante) {
        long minutos = (Math.max(0, tempoRestante.toSeconds()) + 59) / 60;
        if (minutos == 0) {
            return "Conta bloqueada temporariamente. Tente novamente em instantes.";
        }
        return "Conta bloqueada temporariamente. Tente novamente em " + minutos
                + (minutos == 1 ? " minuto." : " minutos.");
    }

    public Duration getTempoRestante() {
        return tempoRestante;
    }

    /** Ver a nota em {@link #CODIGO}: e por este getter que a Sprint 36 le o codigo. */
    public String getCodigo() {
        return CODIGO;
    }
}

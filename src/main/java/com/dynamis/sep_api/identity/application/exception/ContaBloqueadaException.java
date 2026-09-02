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
     *     <p><b>Correcao de 2026-09-02</b>: a primeira versao desta nota dizia que nenhum consumidor
     *     exibe a mensagem. Falso. O {@code sep-app} a mostra <b>verbatim</b> em
     *     {@code /login/verify-totp} ({@code verify-totp.component.ts:81}, que sequer le o
     *     {@code Retry-After}) e em {@code /login} <b>quando o header falta</b>
     *     ({@code login.component.ts:59}); so no caminho normal do {@code /login} o header ganha. O
     *     {@code sep-mobile} descarta o {@code HttpErrorResponse} na navegacao e nunca a mostra.
     *     Alinhar <b>melhora a tela de TOTP hoje</b>, alem de fechar a afirmacao falsa para
     *     integracoes.
     *     <p>Com um numero so, o risco de tipo que motivava o par tambem some: nao ha mais como
     *     trocar minutos por segundos entre dois argumentos adjacentes.
     */
    public ContaBloqueadaException(Duration tempoRestante) {
        super(mensagemDe(tempoRestante));
        this.tempoRestante = tempoRestante;
    }

    /**
     * Arredonda <b>para cima</b>, pelo mesmo motivo do {@code Retry-After} no handler: informar menos
     * do que falta convida o usuario a voltar ainda dentro do bloqueio.
     *
     * <p>Os dois arredondam em <b>unidades diferentes</b> — o header em segundos, esta frase em
     * minutos —, entao nao sao o mesmo numero: com 30s restantes o header diz {@code 30} e o corpo
     * diz "1 minuto". A garantia e mais fraca e suficiente: o corpo nunca anuncia menos que o
     * restante arredondado para minutos inteiros.
     */
    private static String mensagemDe(Duration tempoRestante) {
        long minutos = Math.ceilDiv(Math.max(0L, tempoRestante.toSeconds()), 60);
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

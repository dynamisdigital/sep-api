package com.dynamis.sep_api.identity.application.exception;

import com.dynamis.sep_api.shared.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava o par {@code CODIGO}/{@code getCodigo()} ate a Sprint 36 Task 36.4 lhe dar consumidor em
 * {@code src/main} (Sprint 35 Task 35.5, hotfix do review).
 *
 * <p>A nota no arquivo pede que o proximo levantamento nao apague o par; este teste <b>impede</b>,
 * porque lhe da um consumidor de verdade. Comentario persuade, teste reprova o build — e um
 * comentario nao pode ser verificado por mutacao.
 */
class ContaBloqueadaExceptionTest {

    @Test
    void codigoEstavelExpostoPeloGetter() {
        ContaBloqueadaException ex = new ContaBloqueadaException(Duration.ofMinutes(12));

        assertThat(ContaBloqueadaException.CODIGO).isEqualTo("AUTH-423-001");
        assertThat(ex.getCodigo()).isEqualTo(ContaBloqueadaException.CODIGO);
    }

    /**
     * A razao estrutural de existir constante propria: a hierarquia selada de {@link DomainException}
     * nao permite esta classe, entao ela nao tem como herdar o {@code codigo}. Se alguem um dia
     * incluir a excecao no {@code permits}, este teste falha e obriga a revisar o par duplicado.
     */
    @Test
    void ficaForaDaHierarquiaSeladaDeDomainException() {
        assertThat(DomainException.class.getPermittedSubclasses())
                .isNotNull()
                .doesNotContain(ContaBloqueadaException.class);
        assertThat(DomainException.class.isAssignableFrom(ContaBloqueadaException.class))
                .as("se um dia herdar, a constante e o getter proprios viram duplicacao")
                .isFalse();
    }

    /**
     * Sprint 35 Task 35.7 inverteu esta afirmacao. Ate a 34 a mensagem anunciava a duracao
     * <b>configurada</b> da politica; agora anuncia o que <b>falta deste</b> bloqueio, porque a frase
     * manda esperar e nao enuncia politica.
     */
    @Test
    void mensagemAnunciaOTempoRestanteENaoAPoliticaConfigurada() {
        ContaBloqueadaException ex = new ContaBloqueadaException(Duration.ofMinutes(12));

        assertThat(ex.getMessage())
                .isEqualTo("Conta bloqueada temporariamente. Tente novamente em 12 minutos.")
                .doesNotContain("30");
        assertThat(ex.getTempoRestante()).isEqualTo(Duration.ofMinutes(12));
    }

    /** Arredonda para cima, como o {@code Retry-After}: informar menos convida a voltar bloqueado. */
    @Test
    void arredondaParaCimaEConcordaComORetryAfter() {
        assertThat(new ContaBloqueadaException(Duration.ofSeconds(615)).getMessage())
                .as("10min15s vira 11, e nao 10")
                .contains("11 minutos");
        assertThat(new ContaBloqueadaException(Duration.ofSeconds(61)).getMessage())
                .contains("2 minutos");
    }

    @Test
    void umMinutoExatoNaoSaiNoPlural() {
        assertThat(new ContaBloqueadaException(Duration.ofMinutes(1)).getMessage())
                .isEqualTo("Conta bloqueada temporariamente. Tente novamente em 1 minuto.");
    }

    /**
     * Bloqueio ja vencido, ou com menos de um segundo, nao pode virar "em 0 minutos" — sairia como
     * instrucao absurda. Vira "em instantes", que e verdadeiro e acionavel.
     */
    @Test
    void restanteAbaixoDeUmSegundoNaoAnunciaZero() {
        assertThat(new ContaBloqueadaException(Duration.ZERO).getMessage())
                .isEqualTo("Conta bloqueada temporariamente. Tente novamente em instantes.");
        assertThat(new ContaBloqueadaException(Duration.ofMillis(400)).getMessage())
                .as("400ms nao chega a um segundo; anunciar 1 minuto exageraria 150x")
                .isEqualTo("Conta bloqueada temporariamente. Tente novamente em instantes.");
        assertThat(new ContaBloqueadaException(Duration.ofSeconds(30)).getMessage())
                .as("meio minuto arredonda para cima, nunca para baixo")
                .contains("1 minuto.");
    }
}

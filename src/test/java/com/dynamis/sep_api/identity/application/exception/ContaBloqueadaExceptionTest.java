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
        ContaBloqueadaException ex = new ContaBloqueadaException(30, Duration.ofMinutes(12));

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

    @Test
    void mensagemAnunciaAPoliticaEONaoORestante() {
        ContaBloqueadaException ex = new ContaBloqueadaException(30, Duration.ofMinutes(12));

        assertThat(ex.getMessage()).contains("30 minutos");
        assertThat(ex.getTempoRestante()).isEqualTo(Duration.ofMinutes(12));
    }
}

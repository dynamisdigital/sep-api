package com.dynamis.sep_api.governanca.domain.model;

import com.dynamis.sep_api.governanca.domain.exception.ValorParametroInvalidoException;
import com.dynamis.sep_api.governanca.domain.vo.TipoParametroOperacional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testes de dominio do parametro operacional (Sprint 18 Task 18.4). */
class ParametroOperacionalDomainTest {

    @Test
    void criaParametroValidoComVersaoInicial() {
        ParametroOperacional p =
                ParametroOperacional.criar("credito.score.pre-aprovacao", TipoParametroOperacional.INTEGER, "700", "d");
        assertThat(p.getVersao()).isEqualTo(1);
        assertThat(p.isAtivo()).isTrue();
        assertThat(p.getValor()).isEqualTo("700");
    }

    @Test
    void criaComValorIncompativelComTipoFalha() {
        assertThatThrownBy(() -> ParametroOperacional.criar("x", TipoParametroOperacional.INTEGER, "abc", "d"))
                .isInstanceOf(ValorParametroInvalidoException.class);
    }

    @Test
    void alterarValorIncrementaVersaoERetornaAnterior() {
        ParametroOperacional p = ParametroOperacional.criar("k", TipoParametroOperacional.DECIMAL, "10.00", "d");
        String anterior = p.alterarValor("25.50");
        assertThat(anterior).isEqualTo("10.00");
        assertThat(p.getValor()).isEqualTo("25.50");
        assertThat(p.getVersao()).isEqualTo(2);
    }

    @Test
    void alterarComValorInvalidoFalhaEMantemEstado() {
        ParametroOperacional p = ParametroOperacional.criar("k", TipoParametroOperacional.BOOLEAN, "true", "d");
        assertThatThrownBy(() -> p.alterarValor("talvez")).isInstanceOf(ValorParametroInvalidoException.class);
        assertThat(p.getValor()).isEqualTo("true");
        assertThat(p.getVersao()).isEqualTo(1);
    }

    @Test
    void tipoAceitaValidaCorretamente() {
        assertThat(TipoParametroOperacional.INTEGER.aceita("12")).isTrue();
        assertThat(TipoParametroOperacional.INTEGER.aceita("12.5")).isFalse();
        // fora do range de int -> rejeitado (coerente com lerInteiro/Integer.parseInt)
        assertThat(TipoParametroOperacional.INTEGER.aceita("3000000000")).isFalse();
        assertThat(TipoParametroOperacional.DECIMAL.aceita("12.5")).isTrue();
        assertThat(TipoParametroOperacional.BOOLEAN.aceita("false")).isTrue();
        assertThat(TipoParametroOperacional.BOOLEAN.aceita("x")).isFalse();
        assertThat(TipoParametroOperacional.STRING.aceita("qualquer")).isTrue();
        assertThat(TipoParametroOperacional.STRING.aceita("")).isFalse();
    }
}

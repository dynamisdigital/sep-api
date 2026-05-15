package com.dynamis.sep_api.onboarding.domain.vo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CnpjTest {

    private static final String CNPJ_VALIDO = "11.222.333/0001-81";
    private static final String CNPJ_VALIDO_SEM_MASCARA = "11222333000181";

    @Test
    void aceitaCnpjValidoComMascara() {
        Cnpj cnpj = new Cnpj(CNPJ_VALIDO);

        assertThat(cnpj.valor()).isEqualTo(CNPJ_VALIDO_SEM_MASCARA);
    }

    @Test
    void aceitaCnpjValidoSemMascara() {
        Cnpj cnpj = new Cnpj(CNPJ_VALIDO_SEM_MASCARA);

        assertThat(cnpj.valor()).isEqualTo(CNPJ_VALIDO_SEM_MASCARA);
    }

    @Test
    void rejeitaCnpjNulo() {
        assertThatThrownBy(() -> new Cnpj(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejeitaCnpjComMenosDe14Digitos() {
        assertThatThrownBy(() -> new Cnpj("1122233300018"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("14 digitos");
    }

    @Test
    void rejeitaCnpjComSequenciaRepetida() {
        assertThatThrownBy(() -> new Cnpj("11111111111111"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sequencia repetida");
    }

    @Test
    void rejeitaCnpjComDigitosVerificadoresInvalidos() {
        assertThatThrownBy(() -> new Cnpj("11222333000182"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("digitos verificadores");
    }

    @Test
    void formatadoRetornaPadraoBR() {
        Cnpj cnpj = new Cnpj(CNPJ_VALIDO_SEM_MASCARA);

        assertThat(cnpj.formatado()).isEqualTo(CNPJ_VALIDO);
    }
}

package com.dynamis.sep_api.onboarding.domain.vo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CpfTest {

    private static final String CPF_VALIDO = "529.982.247-25";
    private static final String CPF_VALIDO_SEM_MASCARA = "52998224725";

    @Test
    void aceitaCpfValidoComMascara() {
        Cpf cpf = new Cpf(CPF_VALIDO);

        assertThat(cpf.valor()).isEqualTo(CPF_VALIDO_SEM_MASCARA);
    }

    @Test
    void aceitaCpfValidoSemMascara() {
        Cpf cpf = new Cpf(CPF_VALIDO_SEM_MASCARA);

        assertThat(cpf.valor()).isEqualTo(CPF_VALIDO_SEM_MASCARA);
    }

    @Test
    void rejeitaCpfNulo() {
        assertThatThrownBy(() -> new Cpf(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejeitaCpfComMenosDe11Digitos() {
        assertThatThrownBy(() -> new Cpf("123456789"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("11 digitos");
    }

    @Test
    void rejeitaCpfComSequenciaRepetida() {
        assertThatThrownBy(() -> new Cpf("11111111111"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sequencia repetida");
    }

    @Test
    void rejeitaCpfComDigitosVerificadoresInvalidos() {
        assertThatThrownBy(() -> new Cpf("52998224726"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("digitos verificadores");
    }

    @Test
    void formatadoRetornaPadraoBR() {
        Cpf cpf = new Cpf(CPF_VALIDO_SEM_MASCARA);

        assertThat(cpf.formatado()).isEqualTo(CPF_VALIDO);
    }
}
